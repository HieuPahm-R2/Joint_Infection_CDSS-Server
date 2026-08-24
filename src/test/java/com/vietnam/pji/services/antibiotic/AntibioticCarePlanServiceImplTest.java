package com.vietnam.pji.services.antibiotic;

import com.vietnam.pji.constant.ClinicalDecisionStatus;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.dto.request.AiAntibioticCarePlanRequestDTO;
import com.vietnam.pji.dto.response.AiAntibioticCarePlanResponseDTO;
import com.vietnam.pji.dto.response.AntibioticCarePlanResponseDTO;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.PharmacistFinalDecision;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.PharmacistFinalDecisionRepository;
import com.vietnam.pji.repository.RecommendationFinalSelectionRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.agent.AiServiceClient;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.antibiotic.impl.AntibioticCarePlanServiceImpl;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AntibioticCarePlanServiceImplTest {

    private final RecommendationAccessService accessService = mock(RecommendationAccessService.class);
    private final AiRecommendationRunRepository runRepository = mock(AiRecommendationRunRepository.class);
    private final PharmacistFinalDecisionRepository decisionRepository = mock(PharmacistFinalDecisionRepository.class);
    private final RecommendationFinalSelectionRepository finalSelectionRepository = mock(RecommendationFinalSelectionRepository.class);
    private final EpisodeSnapshotAssemblerService snapshotService = mock(EpisodeSnapshotAssemblerService.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);

    private AntibioticCarePlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AntibioticCarePlanServiceImpl(
                accessService,
                runRepository,
                decisionRepository,
                finalSelectionRepository,
                snapshotService,
                aiServiceClient);
    }

    @Test
    void generatesFromLatestSignedDecisionWithoutPersistingCarePlan() {
        AiRecommendationRun latestDraftRun = run(8L, 8);
        AiRecommendationRun signedRun = run(7L, 7);
        PharmacistFinalDecision draft = decision(latestDraftRun, ClinicalDecisionStatus.DRAFT);
        PharmacistFinalDecision signed = decision(signedRun, ClinicalDecisionStatus.SIGNED);

        when(runRepository.findByEpisodeIdOrderByCreatedAtDesc(91L))
                .thenReturn(List.of(latestDraftRun, signedRun));
        when(decisionRepository.findByRunIdIn(List.of(8L, 7L)))
                .thenReturn(List.of(draft, signed));
        when(snapshotService.buildSnapshot(91L)).thenReturn(
                EpisodeSnapshotAssemblerService.SnapshotBuildResult.builder()
                        .snapshotDataJson(Map.of("snapshot_metadata", Map.of("episode_id", 91L)))
                        .build());
        when(aiServiceClient.generateAntibioticCarePlan(any())).thenReturn(
                AiAntibioticCarePlanResponseDTO.builder()
                        .requestId("care-plan-91")
                        .status("SUCCESS")
                        .latencyMs(1200L)
                        .carePlan(Map.of("category", "ANTIBIOTIC_CARE_PLAN"))
                        .citations(List.of())
                        .build());

        AntibioticCarePlanResponseDTO result = service.generate(91L);

        assertThat(result.getSourceRunId()).isEqualTo(7L);
        assertThat(result.getSourceRunNo()).isEqualTo(7);
        assertThat(result.getCarePlan()).containsEntry("category", "ANTIBIOTIC_CARE_PLAN");
        ArgumentCaptor<AiAntibioticCarePlanRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(AiAntibioticCarePlanRequestDTO.class);
        verify(aiServiceClient).generateAntibioticCarePlan(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getSystemicAntibioticPlan())
                .containsEntry("category", "SYSTEMIC_ANTIBIOTIC");
        assertThat(requestCaptor.getValue().getLocalAntibioticPlan())
                .containsEntry("category", "LOCAL_ANTIBIOTIC");
    }

    @Test
    void rejectsEpisodeWithoutSignedPharmacistDecisionBeforeCallingAi() {
        AiRecommendationRun run = run(8L, 8);
        when(runRepository.findByEpisodeIdOrderByCreatedAtDesc(91L)).thenReturn(List.of(run));
        when(decisionRepository.findByRunIdIn(List.of(8L)))
                .thenReturn(List.of(decision(run, ClinicalDecisionStatus.DRAFT)));

        assertThatThrownBy(() -> service.generate(91L))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no signed pharmacist decision");

        verify(snapshotService, never()).buildSnapshot(any());
        verify(aiServiceClient, never()).generateAntibioticCarePlan(any());
    }

    private AiRecommendationRun run(Long id, int runNo) {
        AiRecommendationRun run = AiRecommendationRun.builder()
                .runNo(runNo)
                .recommendationScope(RecommendationScope.ANTIBIOTIC)
                .build();
        run.setId(id);
        return run;
    }

    private PharmacistFinalDecision decision(
            AiRecommendationRun run,
            ClinicalDecisionStatus status) {
        return PharmacistFinalDecision.builder()
                .run(run)
                .author(User.builder().fullName("DS. Minh Anh").build())
                .status(status)
                .systemicAntibioticPlanJson(Map.of("category", "SYSTEMIC_ANTIBIOTIC"))
                .localAntibioticPlanJson(Map.of("category", "LOCAL_ANTIBIOTIC"))
                .build();
    }
}
