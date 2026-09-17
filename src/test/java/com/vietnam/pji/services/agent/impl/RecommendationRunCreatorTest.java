package com.vietnam.pji.services.agent.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.constant.TriggerType;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.CaseClinicalSnapshot;
import com.vietnam.pji.model.agentic.RecommendationJobOutbox;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.CaseClinicalSnapshotRepository;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.repository.ai.RecommendationJobOutboxRepository;
import com.vietnam.pji.repository.ai.RuleBasedDiagnosticResultRepository;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService.SnapshotBuildResult;

class RecommendationRunCreatorTest {

    private final EpisodeRepository episodeRepository = mock(EpisodeRepository.class);
    private final CaseClinicalSnapshotRepository snapshotRepository = mock(CaseClinicalSnapshotRepository.class);
    private final AiRecommendationRunRepository runRepository = mock(AiRecommendationRunRepository.class);
    private final RuleBasedDiagnosticResultRepository diagnosticRepository = mock(RuleBasedDiagnosticResultRepository.class);
    private final RecommendationJobOutboxRepository outboxRepository = mock(RecommendationJobOutboxRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecommendationRunCreator creator = new RecommendationRunCreator(
            episodeRepository,
            snapshotRepository,
            runRepository,
            diagnosticRepository,
            outboxRepository,
            objectMapper);

    @Test
    void locksEpisodeBeforeAssigningScopedSequenceNumbers() throws Exception {
        PjiEpisode episode = PjiEpisode.builder().build();
        episode.setId(7L);
        when(episodeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(episode));
        when(snapshotRepository.findMaxSnapshotNoByEpisodeId(7L)).thenReturn(3);
        when(runRepository.findMaxRunNoByEpisodeId(7L)).thenReturn(8);
        when(snapshotRepository.save(any(CaseClinicalSnapshot.class))).thenAnswer(invocation -> {
            CaseClinicalSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(101L);
            return snapshot;
        });
        when(runRepository.save(any(AiRecommendationRun.class))).thenAnswer(invocation -> {
            AiRecommendationRun run = invocation.getArgument(0);
            run.setId(102L);
            return run;
        });

        var created = creator.create(
                7L,
                SnapshotBuildResult.builder()
                        .snapshotDataJson(Map.of("patient", "P-1"))
                        .completenessScore(BigDecimal.valueOf(88.5))
                        .build(),
                new PjiDiagnosticRuleEngine.DiagnosticResult("Diagnostic", Map.of(), Map.of(), Map.of()),
                TriggerType.MANUAL_GENERATE,
                RecommendationScope.SURGERY,
                44L);

        assertThat(created.snapshot().getSnapshotNo()).isEqualTo(4);
        assertThat(created.run().getRunNo()).isEqualTo(9);
        assertThat(created.run().getSnapshot()).isSameAs(created.snapshot());
        assertThat(created.run().getCreatedByUserId()).isEqualTo(44L);

        InOrder order = inOrder(episodeRepository, snapshotRepository, runRepository, diagnosticRepository);
        order.verify(episodeRepository).findByIdForUpdate(7L);
        order.verify(snapshotRepository).findMaxSnapshotNoByEpisodeId(7L);
        order.verify(snapshotRepository).save(created.snapshot());
        order.verify(runRepository).findMaxRunNoByEpisodeId(7L);
        order.verify(runRepository).save(created.run());
        order.verify(diagnosticRepository).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void storesAsyncJobPayloadWithTheRun() throws Exception {
        PjiEpisode episode = PjiEpisode.builder().build();
        episode.setId(7L);
        when(episodeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(episode));
        when(snapshotRepository.findMaxSnapshotNoByEpisodeId(7L)).thenReturn(0);
        when(runRepository.findMaxRunNoByEpisodeId(7L)).thenReturn(0);
        when(snapshotRepository.save(any(CaseClinicalSnapshot.class))).thenAnswer(invocation -> {
            CaseClinicalSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(101L);
            return snapshot;
        });
        when(runRepository.save(any(AiRecommendationRun.class))).thenAnswer(invocation -> {
            AiRecommendationRun run = invocation.getArgument(0);
            run.setId(102L);
            return run;
        });

        var snapshotBuild = SnapshotBuildResult.builder()
                .snapshotDataJson(Map.of("patient", "P-1"))
                .completenessScore(BigDecimal.valueOf(88.5))
                .build();
        var diagnostic = new PjiDiagnosticRuleEngine.DiagnosticResult(
                "Diagnostic", Map.of(), Map.of(), Map.of());

        var created = creator.createAsync(
                7L,
                snapshotBuild,
                diagnostic,
                TriggerType.MANUAL_GENERATE,
                RecommendationScope.SURGERY,
                44L);

        ArgumentCaptor<RecommendationJobOutbox> outboxCaptor =
                ArgumentCaptor.forClass(RecommendationJobOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        RecommendationJobOutbox outbox = outboxCaptor.getValue();
        assertThat(outbox.getRun()).isSameAs(created.run());
        assertThat(outbox.getAvailableAt()).isNotNull();

        var payload = objectMapper.readTree(outbox.getPayloadJson());
        assertThat(payload.path("runId").asLong()).isEqualTo(102L);
        assertThat(payload.path("episodeId").asLong()).isEqualTo(7L);
        assertThat(payload.path("snapshotId").asLong()).isEqualTo(101L);
        assertThat(payload.path("recommendationScope").asText()).isEqualTo("SURGERY");
        assertThat(payload.path("snapshotDataJson").path("patient").asText()).isEqualTo("P-1");
    }
}
