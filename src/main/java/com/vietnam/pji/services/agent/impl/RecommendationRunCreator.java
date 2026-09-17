package com.vietnam.pji.services.agent.impl;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.constant.TriggerType;
import com.vietnam.pji.dto.request.RabbitMQRecommendationMessage;
import com.vietnam.pji.dto.request.RuleBasedDiagnosisDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.CaseClinicalSnapshot;
import com.vietnam.pji.model.agentic.RecommendationJobOutbox;
import com.vietnam.pji.model.agentic.RuleBasedDiagnosticResult;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.CaseClinicalSnapshotRepository;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.repository.ai.RecommendationJobOutboxRepository;
import com.vietnam.pji.repository.ai.RuleBasedDiagnosticResultRepository;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService.SnapshotBuildResult;

import lombok.RequiredArgsConstructor;

/**
 * Creates the immutable persisted input for one recommendation run.
 *
 * <p>The episode row is the serialization point: snapshot and run numbers are
 * scoped to that episode, so both counters are assigned while the row is
 * locked. Async creation also stores the RabbitMQ payload in the transactional
 * outbox before this transaction ends; broker I/O happens later.
 */
@Service
@RequiredArgsConstructor
class RecommendationRunCreator {

    private final EpisodeRepository episodeRepository;
    private final CaseClinicalSnapshotRepository snapshotRepository;
    private final AiRecommendationRunRepository runRepository;
    private final RuleBasedDiagnosticResultRepository diagnosticResultRepository;
    private final RecommendationJobOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    CreatedRecommendationRun create(
            Long episodeId,
            SnapshotBuildResult snapshotBuild,
            PjiDiagnosticRuleEngine.DiagnosticResult diagnostic,
            TriggerType triggerType,
            RecommendationScope recommendationScope,
            Long createdByUserId) {
        return createPersistedInput(
                episodeId,
                snapshotBuild,
                diagnostic,
                triggerType,
                recommendationScope,
                createdByUserId,
                false);
    }

    @Transactional
    CreatedRecommendationRun createAsync(
            Long episodeId,
            SnapshotBuildResult snapshotBuild,
            PjiDiagnosticRuleEngine.DiagnosticResult diagnostic,
            TriggerType triggerType,
            RecommendationScope recommendationScope,
            Long createdByUserId) {
        return createPersistedInput(
                episodeId,
                snapshotBuild,
                diagnostic,
                triggerType,
                recommendationScope,
                createdByUserId,
                true);
    }

    private CreatedRecommendationRun createPersistedInput(
            Long episodeId,
            SnapshotBuildResult snapshotBuild,
            PjiDiagnosticRuleEngine.DiagnosticResult diagnostic,
            TriggerType triggerType,
            RecommendationScope recommendationScope,
            Long createdByUserId,
            boolean enqueueAsyncJob) {
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

        if (enqueueAsyncJob) {
            RabbitMQRecommendationMessage message = RabbitMQRecommendationMessage.builder()
                    .requestId(run.getRequestId())
                    .runId(run.getId())
                    .episodeId(episodeId)
                    .snapshotId(snapshot.getId())
                    .triggerType(triggerType.name())
                    .recommendationScope(recommendationScope.name())
                    .snapshotDataJson(snapshotBuild.getSnapshotDataJson())
                    .ruleBasedDiagnosis(RuleBasedDiagnosisDTO.from(diagnostic))
                    .options(Map.of("language", "vi", "include_citations", true, "top_k", 5))
                    .build();
            outboxRepository.save(RecommendationJobOutbox.builder()
                    .run(run)
                    .payloadJson(serializeMessage(message))
                    .availableAt(Instant.now())
                    .build());
        }

        return new CreatedRecommendationRun(snapshot, run);
    }

    private String serializeSnapshot(SnapshotBuildResult snapshotBuild) {
        try {
            return objectMapper.writeValueAsString(snapshotBuild.getSnapshotDataJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize recommendation snapshot", exception);
        }
    }

    private String serializeMessage(RabbitMQRecommendationMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize recommendation outbox job", exception);
        }
    }

    record CreatedRecommendationRun(CaseClinicalSnapshot snapshot, AiRecommendationRun run) {
    }
}
