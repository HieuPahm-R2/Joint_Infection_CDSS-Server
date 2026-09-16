package com.vietnam.pji.services.agent.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.constant.TriggerType;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.CaseClinicalSnapshot;
import com.vietnam.pji.model.agentic.RuleBasedDiagnosticResult;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.CaseClinicalSnapshotRepository;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.repository.ai.RuleBasedDiagnosticResultRepository;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService.SnapshotBuildResult;

import lombok.RequiredArgsConstructor;

/**
 * Creates the immutable persisted input for one recommendation run.
 *
 * <p>The episode row is the serialization point: snapshot and run numbers are
 * scoped to that episode, so both counters are assigned while the row is
 * locked. This transaction intentionally ends before any AI or RabbitMQ I/O.
 */
@Service
@RequiredArgsConstructor
class RecommendationRunCreator {

    private final EpisodeRepository episodeRepository;
    private final CaseClinicalSnapshotRepository snapshotRepository;
    private final AiRecommendationRunRepository runRepository;
    private final RuleBasedDiagnosticResultRepository diagnosticResultRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    CreatedRecommendationRun create(
            Long episodeId,
            SnapshotBuildResult snapshotBuild,
            PjiDiagnosticRuleEngine.DiagnosticResult diagnostic,
            TriggerType triggerType,
            RecommendationScope recommendationScope,
            Long createdByUserId) {
        PjiEpisode episode = episodeRepository.findByIdForUpdate(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found: " + episodeId));

        CaseClinicalSnapshot snapshot = CaseClinicalSnapshot.builder()
                .episode(episode)
                .snapshotNo(snapshotRepository.findMaxSnapshotNoByEpisodeId(episodeId) + 1)
                .dataCompletenessScore(snapshotBuild.getCompletenessScore())
                .snapshotDataJson(serializeSnapshot(snapshotBuild))
                .build();
        snapshot = snapshotRepository.save(snapshot);

        AiRecommendationRun run = AiRecommendationRun.builder()
                .episode(episode)
                .snapshot(snapshot)
                .runNo(runRepository.findMaxRunNoByEpisodeId(episodeId) + 1)
                .triggerType(triggerType)
                .recommendationScope(recommendationScope)
                .status(RunStatus.PROCESSING)
                .requestId(UUID.randomUUID().toString())
                .createdByUserId(createdByUserId)
                .build();
        run = runRepository.save(run);

        diagnosticResultRepository.save(RuleBasedDiagnosticResult.builder()
                .run(run)
                .title(diagnostic.title())
                .itemJson(diagnostic.itemJson())
                .assessmentJson(diagnostic.assessmentJson())
                .explanationJson(diagnostic.explanationJson())
                .build());

        return new CreatedRecommendationRun(snapshot, run);
    }

    private String serializeSnapshot(SnapshotBuildResult snapshotBuild) {
        try {
            return objectMapper.writeValueAsString(snapshotBuild.getSnapshotDataJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize recommendation snapshot", exception);
        }
    }

    record CreatedRecommendationRun(CaseClinicalSnapshot snapshot, AiRecommendationRun run) {
    }
}
