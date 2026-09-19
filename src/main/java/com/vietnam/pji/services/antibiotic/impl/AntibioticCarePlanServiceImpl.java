package com.vietnam.pji.services.antibiotic.impl;

import com.vietnam.pji.constant.ClinicalDecisionStatus;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.dto.request.AiAntibioticCarePlanRequestDTO;
import com.vietnam.pji.dto.response.AiAntibioticCarePlanResponseDTO;
import com.vietnam.pji.dto.response.AntibioticCarePlanResponseDTO;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.PharmacistFinalDecision;
import com.vietnam.pji.repository.PharmacistFinalDecisionRepository;
import com.vietnam.pji.repository.RecommendationFinalSelectionRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.agent.AiServiceClient;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.antibiotic.AntibioticCarePlanService;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AntibioticCarePlanServiceImpl implements AntibioticCarePlanService {

    private final RecommendationAccessService recommendationAccessService;
    private final AiRecommendationRunRepository runRepository;
    private final PharmacistFinalDecisionRepository pharmacistDecisionRepository;
    private final RecommendationFinalSelectionRepository finalSelectionRepository;
    private final EpisodeSnapshotAssemblerService snapshotAssemblerService;
    private final AiServiceClient aiServiceClient;

    @Override
    @Transactional(readOnly = true)
    public AntibioticCarePlanResponseDTO generate(Long episodeId) {
        recommendationAccessService.assertCanReviewEpisode(episodeId);
        SourceDecision source = findLatestSignedDecision(episodeId);
        EpisodeSnapshotAssemblerService.SnapshotBuildResult snapshot =
                snapshotAssemblerService.buildSnapshot(episodeId);

        String requestId = "care-plan-" + episodeId + "-" + UUID.randomUUID();
        AiAntibioticCarePlanResponseDTO aiResponse = aiServiceClient.generateAntibioticCarePlan(
                AiAntibioticCarePlanRequestDTO.builder()
                        .requestId(requestId)
                        .episodeId(episodeId)
                        .snapshotDataJson(snapshot.getSnapshotDataJson())
                        .systemicAntibioticPlan(source.decision().getSystemicAntibioticPlanJson())
                        .localAntibioticPlan(source.decision().getLocalAntibioticPlanJson())
                        .build());

        if (aiResponse == null || aiResponse.getCarePlan() == null) {
            throw new InvalidDataException("AI service did not return an antibiotic care plan");
        }

        return AntibioticCarePlanResponseDTO.builder()
                .requestId(aiResponse.getRequestId())
                .status(aiResponse.getStatus())
                .model(aiResponse.getModel())
                .latencyMs(aiResponse.getLatencyMs())
                .episodeId(episodeId)
                .sourceRunId(source.run().getId())
                .sourceRunNo(source.run().getRunNo())
                .pharmacistName(source.decision().getAuthor() != null
                        ? source.decision().getAuthor().getFullName()
                        : null)
                .carePlan(aiResponse.getCarePlan())
                .citations(aiResponse.getCitations())
                .build();
    }

    private SourceDecision findLatestSignedDecision(Long episodeId) {
        List<AiRecommendationRun> runs = runRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
        if (runs.isEmpty()) {
            throw new InvalidDataException("Episode has no antibiotic recommendation run");
        }

        List<Long> runIds = runs.stream().map(AiRecommendationRun::getId).toList();
        Map<Long, PharmacistFinalDecision> decisions = pharmacistDecisionRepository
                .findByRunIdIn(runIds)
                .stream()
                .collect(Collectors.toMap(item -> item.getRun().getId(), Function.identity()));

        Long selectedRunId = finalSelectionRepository
                .findByEpisodeIdAndRecommendationScope(episodeId, RecommendationScope.ANTIBIOTIC)
                .map(selection -> selection.getRun().getId())
                .orElse(null);
        if (selectedRunId != null) {
            AiRecommendationRun selectedRun = runs.stream()
                    .filter(run -> selectedRunId.equals(run.getId()))
                    .findFirst()
                    .orElse(null);
            PharmacistFinalDecision selectedDecision = decisions.get(selectedRunId);
            if (selectedRun != null
                    && selectedDecision != null
                    && selectedDecision.getStatus() == ClinicalDecisionStatus.SIGNED) {
                requirePlan(selectedDecision.getSystemicAntibioticPlanJson(), "systemic antibiotic");
                requirePlan(selectedDecision.getLocalAntibioticPlanJson(), "local antibiotic");
                return new SourceDecision(selectedRun, selectedDecision);
            }
        }

        for (AiRecommendationRun run : runs) {
            if (run.getRecommendationScope() != RecommendationScope.ANTIBIOTIC) {
                continue;
            }
            PharmacistFinalDecision decision = decisions.get(run.getId());
            if (decision != null && decision.getStatus() == ClinicalDecisionStatus.SIGNED) {
                requirePlan(decision.getSystemicAntibioticPlanJson(), "systemic antibiotic");
                requirePlan(decision.getLocalAntibioticPlanJson(), "local antibiotic");
                return new SourceDecision(run, decision);
            }
        }

        throw new InvalidDataException(
                "Episode has no signed pharmacist decision with an antibiotic regimen");
    }

    private void requirePlan(Map<String, Object> plan, String label) {
        if (plan == null || plan.isEmpty()) {
            throw new InvalidDataException("Signed pharmacist decision is missing " + label + " plan");
        }
    }

    private record SourceDecision(AiRecommendationRun run, PharmacistFinalDecision decision) {
    }
}
