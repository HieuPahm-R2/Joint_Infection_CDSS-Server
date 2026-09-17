package com.vietnam.pji.services.agent.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.constant.TriggerType;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.CaseClinicalSnapshot;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiRagCitationRepository;
import com.vietnam.pji.repository.ai.AiRecommendationItemRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.repository.ai.RuleBasedDiagnosticResultRepository;
import com.vietnam.pji.services.agent.AiServiceClient;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.mapper.AiRecommendationRunMapper;

class AiRecommendationServiceImplTest {

    private final EpisodeRepository episodeRepository = mock(EpisodeRepository.class);
    private final AiRecommendationRunRepository runRepository = mock(AiRecommendationRunRepository.class);
    private final AiRecommendationItemRepository itemRepository = mock(AiRecommendationItemRepository.class);
    private final AiRagCitationRepository citationRepository = mock(AiRagCitationRepository.class);
    private final RuleBasedDiagnosticResultRepository diagnosticRepository = mock(RuleBasedDiagnosticResultRepository.class);
    private final EpisodeSnapshotAssemblerService snapshotAssembler = mock(EpisodeSnapshotAssemblerService.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final PjiDiagnosticRuleEngine ruleEngine = mock(PjiDiagnosticRuleEngine.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final RedisService redisService = mock(RedisService.class);
    private final AiRecommendationRunMapper runMapper = mock(AiRecommendationRunMapper.class);
    private final RecommendationAccessService accessService = mock(RecommendationAccessService.class);
    private final RecommendationRunCreator runCreator = mock(RecommendationRunCreator.class);
    private final AiRecommendationServiceImpl service = new AiRecommendationServiceImpl(
            episodeRepository,
            runRepository,
            itemRepository,
            citationRepository,
            diagnosticRepository,
            snapshotAssembler,
            aiServiceClient,
            ruleEngine,
            objectMapper,
            redisService,
            runMapper,
            accessService,
            runCreator);

    @Test
    void asyncGenerationPersistsTheOutboxJobWithoutPublishingInline() {
        AiRecommendationRun run = AiRecommendationRun.builder()
                .episode(PjiEpisode.builder().build())
                .snapshot(CaseClinicalSnapshot.builder().build())
                .requestId("request-1")
                .status(RunStatus.PROCESSING)
                .build();
        run.setId(12L);
        var snapshot = EpisodeSnapshotAssemblerService.SnapshotBuildResult.builder()
                .snapshotDataJson(Map.of("patient", "P-1"))
                .completenessScore(BigDecimal.TEN)
                .build();
        var diagnostic = new PjiDiagnosticRuleEngine.DiagnosticResult("Diagnostic", Map.of(), Map.of(), Map.of());
        when(snapshotAssembler.buildSnapshot(7L)).thenReturn(snapshot);
        when(ruleEngine.evaluate(snapshot.getSnapshotDataJson())).thenReturn(diagnostic);
        when(runCreator.createAsync(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RecommendationRunCreator.CreatedRecommendationRun(run.getSnapshot(), run));

        var result = service.generateRecommendationAsync(
                7L, TriggerType.MANUAL_GENERATE, RecommendationScope.SURGERY);

        assertThat(result).isNotNull();
        assertThat(run.getStatus()).isEqualTo(RunStatus.PROCESSING);
        verify(runCreator).createAsync(any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryCreatesANewRunThroughTheAsyncOutboxPath() {
        PjiEpisode episode = PjiEpisode.builder().build();
        episode.setId(7L);
        AiRecommendationRun failedRun = AiRecommendationRun.builder()
                .episode(episode)
                .triggerType(TriggerType.MANUAL_GENERATE)
                .recommendationScope(RecommendationScope.SURGERY)
                .status(RunStatus.FAILED)
                .build();
        failedRun.setId(11L);
        AiRecommendationRun retryRun = AiRecommendationRun.builder()
                .episode(episode)
                .snapshot(CaseClinicalSnapshot.builder().build())
                .requestId("request-2")
                .status(RunStatus.PROCESSING)
                .build();
        retryRun.setId(12L);
        var snapshot = EpisodeSnapshotAssemblerService.SnapshotBuildResult.builder()
                .snapshotDataJson(Map.of("patient", "P-1"))
                .completenessScore(BigDecimal.TEN)
                .build();
        var diagnostic = new PjiDiagnosticRuleEngine.DiagnosticResult(
                "Diagnostic", Map.of(), Map.of(), Map.of());
        when(runRepository.findById(11L)).thenReturn(Optional.of(failedRun));
        when(snapshotAssembler.buildSnapshot(7L)).thenReturn(snapshot);
        when(ruleEngine.evaluate(snapshot.getSnapshotDataJson())).thenReturn(diagnostic);
        when(runCreator.createAsync(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RecommendationRunCreator.CreatedRecommendationRun(
                        retryRun.getSnapshot(), retryRun));

        var result = service.retryRun(11L);

        assertThat(result).isNotNull();
        verify(runCreator).createAsync(any(), any(), any(), any(), any(), any());
    }
}
