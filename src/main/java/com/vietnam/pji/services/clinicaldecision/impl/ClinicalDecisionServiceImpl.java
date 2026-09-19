package com.vietnam.pji.services.clinicaldecision.impl;

import com.vietnam.pji.constant.ClinicalDecisionStatus;
import com.vietnam.pji.constant.ReviewStatus;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.dto.request.ClinicalDecisionRevisionRequestDTO;
import com.vietnam.pji.dto.request.DoctorClinicalDecisionRequestDTO;
import com.vietnam.pji.dto.request.PharmacistClinicalDecisionRequestDTO;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO.Actor;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO.DoctorDecision;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO.PharmacistDecision;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO.RunDecision;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.DoctorFinalDecision;
import com.vietnam.pji.model.agentic.DoctorRecommendationReview;
import com.vietnam.pji.model.agentic.PharmacistFinalDecision;
import com.vietnam.pji.model.agentic.RecommendationFinalSelection;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.DoctorFinalDecisionRepository;
import com.vietnam.pji.repository.DoctorRecommendationReviewRepository;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.PharmacistFinalDecisionRepository;
import com.vietnam.pji.repository.RecommendationFinalSelectionRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.services.clinicaldecision.ClinicalDecisionService;
import com.vietnam.pji.utils.SecurityUtils;
import com.vietnam.pji.utils.mapper.AiRecommendationRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClinicalDecisionServiceImpl implements ClinicalDecisionService {

    private final AiRecommendationRunRepository runRepository;
    private final EpisodeRepository episodeRepository;
    private final DoctorRecommendationReviewRepository reviewRepository;
    private final DoctorFinalDecisionRepository doctorDecisionRepository;
    private final PharmacistFinalDecisionRepository pharmacistDecisionRepository;
    private final RecommendationFinalSelectionRepository finalSelectionRepository;
    private final RecommendationAccessService recommendationAccessService;
    private final UserService userService;
    private final AiRecommendationRunMapper runMapper;

    @Override
    @Transactional(readOnly = true)
    public ClinicalDecisionWorkspaceDTO getWorkspace(Long episodeId) {
        recommendationAccessService.assertCanReviewEpisode(episodeId);
        List<AiRecommendationRun> runs = runRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
        if (runs.isEmpty()) {
            return ClinicalDecisionWorkspaceDTO.builder()
                    .episodeId(episodeId)
                    .runs(Collections.emptyList())
                    .build();
        }

        List<Long> runIds = runs.stream().map(AiRecommendationRun::getId).toList();
        Map<Long, DoctorFinalDecision> doctorByRun = doctorDecisionRepository.findByRunIdIn(runIds).stream()
                .collect(Collectors.toMap(item -> item.getRun().getId(), Function.identity()));
        Map<Long, PharmacistFinalDecision> pharmacistByRun = pharmacistDecisionRepository.findByRunIdIn(runIds).stream()
                .collect(Collectors.toMap(item -> item.getRun().getId(), Function.identity()));
        Map<RecommendationScope, Long> finalRunIds = finalRunIds(episodeId);
        Long finalDoctorRunId = finalRunIds.get(RecommendationScope.SURGERY);
        Long finalPharmacistRunId = finalRunIds.get(RecommendationScope.ANTIBIOTIC);
        Set<Long> selectedRunIds = new HashSet<>(finalRunIds.values());
        selectedRunIds.remove(null);
        User currentUser = currentUser();

        List<RunDecision> rows = runs.stream()
                .map(run -> toRunDecision(
                        run,
                        doctorByRun.get(run.getId()),
                        pharmacistByRun.get(run.getId()),
                        selectedRunIds,
                        currentUser))
                .toList();

        return ClinicalDecisionWorkspaceDTO.builder()
                .episodeId(episodeId)
                .finalRunId(finalDoctorRunId)
                .finalDoctorRunId(finalDoctorRunId)
                .finalPharmacistRunId(finalPharmacistRunId)
                .runs(rows)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RunDecision getRunDecision(Long runId) {
        recommendationAccessService.assertCanReviewRun(runId);
        AiRecommendationRun run = findRun(runId);
        Set<Long> selectedRunIds = selectedRunIds(run.getEpisode().getId());
        return toRunDecision(
                run,
                doctorDecisionRepository.findByRunId(runId).orElse(null),
                pharmacistDecisionRepository.findByRunId(runId).orElse(null),
                selectedRunIds,
                currentUser());
    }

    @Override
    @Transactional
    public RunDecision saveDoctorDecision(Long runId, DoctorClinicalDecisionRequestDTO request) {
        AiRecommendationRun run = findRunForUpdate(runId);
        User currentUser = requireRole(currentUser(), "DOCTOR", "ADMIN", "SUPER_ADMIN");
        requireReviewable(run);
        requireDoctorScope(run);
        requireDoctorRunOwner(run, currentUser);

        DoctorFinalDecision decision = doctorDecisionRepository.findByRunId(runId).orElse(null);
        if (decision == null) {
            requireNewRevision(request.getRevision());
            DoctorRecommendationReview review = reviewRepository.findByRunId(runId)
                    .orElseGet(() -> reviewRepository.save(DoctorRecommendationReview.builder()
                            .episode(run.getEpisode())
                            .run(run)
                            .reviewStatus(ReviewStatus.SAVED_DRAFT)
                            .build()));
            decision = DoctorFinalDecision.builder()
                    .review(review)
                    .run(run)
                    .author(currentUser)
                    .status(ClinicalDecisionStatus.DRAFT)
                    .diagnosisJson(request.getDiagnosisJson())
                    .surgeryPlanJson(request.getSurgeryPlanJson())
                    .build();
        } else {
            requireDraftOwner(decision.getAuthor(), decision.getStatus(), currentUser, request.getRevision(), decision.getVersion());
            decision.setDiagnosisJson(request.getDiagnosisJson());
            decision.setSurgeryPlanJson(request.getSurgeryPlanJson());
        }

        DoctorFinalDecision saved = doctorDecisionRepository.saveAndFlush(decision);
        DoctorRecommendationReview review = saved.getReview();
        review.setDoctorFinalDecision(saved);
        review.setDoctorDiagnosisJson(saved.getDiagnosisJson());
        review.setModificationJson(mergeSurgery(review.getModificationJson(), saved.getSurgeryPlanJson()));
        review.setReviewStatus(ReviewStatus.SAVED_DRAFT);
        reviewRepository.save(review);
        return currentRunDecision(run, currentUser);
    }

    @Override
    @Transactional
    public RunDecision signDoctorDecision(Long runId, ClinicalDecisionRevisionRequestDTO request) {
        AiRecommendationRun run = findRunForUpdate(runId);
        User currentUser = requireRole(currentUser(), "DOCTOR", "ADMIN", "SUPER_ADMIN");
        requireReviewable(run);
        requireDoctorScope(run);
        DoctorFinalDecision decision = doctorDecisionRepository.findByRunId(runId)
                .orElseThrow(() -> new InvalidDataException("Doctor decision must be saved before signing"));
        requireDraftOwner(decision.getAuthor(), decision.getStatus(), currentUser, request.getRevision(), decision.getVersion());
        decision.setStatus(ClinicalDecisionStatus.SIGNED);
        decision.setSignedAt(Instant.now());
        doctorDecisionRepository.saveAndFlush(decision);
        decision.getReview().setReviewStatus(ReviewStatus.ACCEPTED);
        reviewRepository.save(decision.getReview());
        return currentRunDecision(run, currentUser);
    }

    @Override
    @Transactional
    public RunDecision savePharmacistDecision(Long runId, PharmacistClinicalDecisionRequestDTO request) {
        AiRecommendationRun run = findRunForUpdate(runId);
        User currentUser = requireRole(currentUser(), "PHARMACIST", "ADMIN", "SUPER_ADMIN");
        requireReviewable(run);
        requirePharmacistScope(run);
        requirePharmacistRunOwner(run, currentUser);

        PharmacistFinalDecision decision = pharmacistDecisionRepository.findByRunId(runId).orElse(null);
        if (decision == null) {
            requireNewRevision(request.getRevision());
            decision = PharmacistFinalDecision.builder()
                    .run(run)
                    .author(currentUser)
                    .status(ClinicalDecisionStatus.DRAFT)
                    .build();
        } else {
            requireDraftOwner(decision.getAuthor(), decision.getStatus(), currentUser, request.getRevision(), decision.getVersion());
        }
        decision.setSystemicAntibioticPlanJson(request.getSystemicAntibioticPlanJson());
        decision.setLocalAntibioticPlanJson(request.getLocalAntibioticPlanJson());
        decision.setCarePlanJson(request.getCarePlanJson());
        decision.setNotes(request.getNotes());
        pharmacistDecisionRepository.saveAndFlush(decision);
        return currentRunDecision(run, currentUser);
    }

    @Override
    @Transactional
    public RunDecision signPharmacistDecision(Long runId, ClinicalDecisionRevisionRequestDTO request) {
        AiRecommendationRun run = findRunForUpdate(runId);
        User currentUser = requireRole(currentUser(), "PHARMACIST", "ADMIN", "SUPER_ADMIN");
        requireReviewable(run);
        requirePharmacistScope(run);
        requirePharmacistRunOwner(run, currentUser);
        PharmacistFinalDecision decision = pharmacistDecisionRepository.findByRunId(runId)
                .orElseThrow(() -> new InvalidDataException("Pharmacist decision must be saved before signing"));
        requireDraftOwner(decision.getAuthor(), decision.getStatus(), currentUser, request.getRevision(), decision.getVersion());
        decision.setStatus(ClinicalDecisionStatus.SIGNED);
        decision.setSignedAt(Instant.now());
        pharmacistDecisionRepository.saveAndFlush(decision);
        return currentRunDecision(run, currentUser);
    }

    @Override
    @Transactional
    public RunDecision selectFinalRun(Long episodeId, Long runId) {
        AiRecommendationRun run = findRunForUpdate(runId);
        if (run.getEpisode() == null || !episodeId.equals(run.getEpisode().getId())) {
            throw new ForbiddenException("Recommendation run does not belong to this episode");
        }
        requireReviewable(run);
        RecommendationScope scope = scopeOf(run);
        User currentUser = currentUser();
        DoctorFinalDecision doctor = doctorDecisionRepository.findByRunId(runId).orElse(null);
        PharmacistFinalDecision pharmacist = pharmacistDecisionRepository.findByRunId(runId).orElse(null);
        requireFinalSelectionOwner(scope, doctor, pharmacist, currentUser);

        RecommendationFinalSelection selection = finalSelectionRepository
                .findByEpisodeIdAndRecommendationScope(episodeId, scope)
                .orElseGet(() -> RecommendationFinalSelection.builder()
                        .episode(episodeRepository.findById(episodeId)
                                .orElseThrow(() -> new ResourceNotFoundException("Episode not found: " + episodeId)))
                        .recommendationScope(scope)
                        .build());
        selection.setRun(run);
        selection.setSelectedBy(currentUser);
        selection.setSelectedAt(Instant.now());
        finalSelectionRepository.save(selection);

        if (scope.supportsDoctorDecision()) {
            reviewRepository.clearFinalDecisionForEpisode(episodeId);
            reviewRepository.findByRunId(runId).ifPresent(review -> {
                review.setFinalDecision(true);
                reviewRepository.save(review);
            });
        }
        return toRunDecision(run, doctor, pharmacist, Set.of(runId), currentUser);
    }

    private RunDecision currentRunDecision(AiRecommendationRun run, User currentUser) {
        return toRunDecision(
                run,
                doctorDecisionRepository.findByRunId(run.getId()).orElse(null),
                pharmacistDecisionRepository.findByRunId(run.getId()).orElse(null),
                selectedRunIds(run.getEpisode().getId()),
                currentUser);
    }

    private RunDecision toRunDecision(
            AiRecommendationRun run,
            DoctorFinalDecision doctor,
            PharmacistFinalDecision pharmacist,
            Set<Long> finalRunIds,
            User currentUser) {
        RecommendationScope scope = scopeOf(run);
        boolean doctorSigned = doctor != null && doctor.getStatus() == ClinicalDecisionStatus.SIGNED;
        boolean pharmacistSigned = pharmacist != null && pharmacist.getStatus() == ClinicalDecisionStatus.SIGNED;
        boolean doctorLane = scope.supportsDoctorDecision();
        boolean pharmacistLane = scope.supportsPharmacistDecision();
        boolean canEditDoctor = doctorLane
                && isRole(currentUser, "DOCTOR", "ADMIN", "SUPER_ADMIN")
                && runOwnedBy(run, currentUser)
                && (doctor == null || (doctor.getStatus() == ClinicalDecisionStatus.DRAFT && sameUser(doctor.getAuthor(), currentUser)));
        boolean canEditPharmacist = pharmacistLane
                && isRole(currentUser, "PHARMACIST", "ADMIN", "SUPER_ADMIN")
                && runOwnedBy(run, currentUser)
                && (pharmacist == null
                    || (pharmacist.getStatus() == ClinicalDecisionStatus.DRAFT && sameUser(pharmacist.getAuthor(), currentUser)));
        boolean eligibleForFinal = switch (scope) {
            case SURGERY -> doctorSigned;
            case ANTIBIOTIC -> pharmacistSigned;
        };
        boolean canSelectFinal = switch (scope) {
            case SURGERY -> doctorSigned && doctor != null && sameUser(doctor.getAuthor(), currentUser);
            case ANTIBIOTIC -> pharmacistSigned && pharmacist != null && sameUser(pharmacist.getAuthor(), currentUser);
        };
        return RunDecision.builder()
                .run(runMapper.toDto(run))
                .doctorDecision(toDoctorDecision(doctor))
                .pharmacistDecision(toPharmacistDecision(pharmacist))
                .finalSelection(finalRunIds.contains(run.getId()))
                .eligibleForFinal(eligibleForFinal)
                .canEditDoctor(canEditDoctor)
                .canEditPharmacist(canEditPharmacist)
                .canSelectFinal(canSelectFinal)
                .build();
    }

    private DoctorDecision toDoctorDecision(DoctorFinalDecision decision) {
        if (decision == null) return null;
        return DoctorDecision.builder()
                .id(decision.getId())
                .status(decision.getStatus().name())
                .author(toActor(decision.getAuthor()))
                .diagnosisJson(decision.getDiagnosisJson())
                .surgeryPlanJson(decision.getSurgeryPlanJson())
                .signedAt(decision.getSignedAt())
                .revision(decision.getVersion())
                .createdAt(decision.getCreatedAt())
                .updatedAt(decision.getUpdatedAt())
                .build();
    }

    private PharmacistDecision toPharmacistDecision(PharmacistFinalDecision decision) {
        if (decision == null) return null;
        return PharmacistDecision.builder()
                .id(decision.getId())
                .status(decision.getStatus().name())
                .author(toActor(decision.getAuthor()))
                .systemicAntibioticPlanJson(decision.getSystemicAntibioticPlanJson())
                .localAntibioticPlanJson(decision.getLocalAntibioticPlanJson())
                .carePlanJson(decision.getCarePlanJson())
                .notes(decision.getNotes())
                .signedAt(decision.getSignedAt())
                .revision(decision.getVersion())
                .createdAt(decision.getCreatedAt())
                .updatedAt(decision.getUpdatedAt())
                .build();
    }

    private Actor toActor(User user) {
        if (user == null) return null;
        return Actor.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    private AiRecommendationRun findRun(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));
    }

    private AiRecommendationRun findRunForUpdate(Long runId) {
        return runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));
    }

    private void requireReviewable(AiRecommendationRun run) {
        if (run.getStatus() != RunStatus.SUCCESS && run.getStatus() != RunStatus.PARTIAL) {
            throw new InvalidDataException("Only completed recommendation runs can be reviewed");
        }
    }

    private RecommendationScope scopeOf(AiRecommendationRun run) {
        if (run.getRecommendationScope() == null) {
            throw new InvalidDataException("Recommendation scope is required");
        }
        return run.getRecommendationScope();
    }

    private void requireDoctorScope(AiRecommendationRun run) {
        if (!scopeOf(run).supportsDoctorDecision()) {
            throw new InvalidDataException("Doctor decisions are only valid for surgery runs");
        }
    }

    private void requirePharmacistScope(AiRecommendationRun run) {
        if (!scopeOf(run).supportsPharmacistDecision()) {
            throw new InvalidDataException("Pharmacist decisions are only valid for antibiotic runs");
        }
    }

    private Map<RecommendationScope, Long> finalRunIds(Long episodeId) {
        Map<RecommendationScope, Long> result = new EnumMap<>(RecommendationScope.class);
        finalSelectionRepository.findAllByEpisodeId(episodeId).forEach(selection -> {
            RecommendationScope scope = selection.getRecommendationScope();
            if (selection.getRun() != null) {
                result.put(scope, selection.getRun().getId());
            }
        });
        return result;
    }

    private Set<Long> selectedRunIds(Long episodeId) {
        Set<Long> selected = new HashSet<>(finalRunIds(episodeId).values());
        selected.remove(null);
        return selected;
    }

    private void requireFinalSelectionOwner(
            RecommendationScope scope,
            DoctorFinalDecision doctor,
            PharmacistFinalDecision pharmacist,
            User currentUser) {
        switch (scope) {
            case SURGERY -> {
                requireRole(currentUser, "DOCTOR", "ADMIN", "SUPER_ADMIN");
                if (doctor == null || doctor.getStatus() != ClinicalDecisionStatus.SIGNED) {
                    throw new InvalidDataException("A signed doctor decision is required");
                }
                if (!sameUser(doctor.getAuthor(), currentUser)) {
                    throw new ForbiddenException("Only the doctor who owns this decision can select it");
                }
            }
            case ANTIBIOTIC -> {
                requireRole(currentUser, "PHARMACIST", "ADMIN", "SUPER_ADMIN");
                if (pharmacist == null || pharmacist.getStatus() != ClinicalDecisionStatus.SIGNED) {
                    throw new InvalidDataException("A signed pharmacist decision is required");
                }
                if (!sameUser(pharmacist.getAuthor(), currentUser)) {
                    throw new ForbiddenException("Only the pharmacist who owns this decision can select it");
                }
            }
        }
    }

    private User currentUser() {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ForbiddenException("Authenticated user required"));
        User user = userService.handleGetUserByUsername(email);
        if (user == null || user.getId() == null) {
            throw new ForbiddenException("Authenticated user required");
        }
        return user;
    }

    private User requireRole(User user, String... roles) {
        if (!isRole(user, roles)) {
            throw new ForbiddenException("Your role cannot update this clinical decision");
        }
        return user;
    }

    private boolean isRole(User user, String... roles) {
        String currentRole = user != null && user.getRole() != null ? user.getRole().getName() : "";
        for (String role : roles) {
            if (role.equalsIgnoreCase(currentRole)) return true;
        }
        return false;
    }

    private void requireDoctorRunOwner(AiRecommendationRun run, User currentUser) {
        if (!runOwnedBy(run, currentUser)) {
            throw new ForbiddenException("Only the doctor who created this AI run can edit its doctor decision");
        }
    }

    private void requirePharmacistRunOwner(AiRecommendationRun run, User currentUser) {
        if (!runOwnedBy(run, currentUser)) {
            throw new ForbiddenException("Only the pharmacist who created this AI run can edit its decision");
        }
    }

    private boolean runOwnedBy(AiRecommendationRun run, User user) {
        if (run.getCreatedByUserId() != null) {
            return run.getCreatedByUserId().equals(user.getId());
        }
        return run.getCreatedBy() != null && user.getEmail() != null
                && run.getCreatedBy().trim().equalsIgnoreCase(user.getEmail().trim());
    }

    private void requireDraftOwner(
            User author,
            ClinicalDecisionStatus status,
            User currentUser,
            Long requestedRevision,
            Long currentRevision) {
        if (!sameUser(author, currentUser)) {
            throw new ForbiddenException("This clinical decision belongs to another user");
        }
        if (status == ClinicalDecisionStatus.SIGNED) {
            throw new InvalidDataException("Signed clinical decisions are immutable");
        }
        if (requestedRevision == null || !requestedRevision.equals(currentRevision)) {
            throw new InvalidDataException("This decision was changed elsewhere. Reload before saving");
        }
    }

    private void requireNewRevision(Long revision) {
        if (revision == null || revision != 0L) {
            throw new InvalidDataException("A new clinical decision must start at revision 0");
        }
    }

    private boolean sameUser(User left, User right) {
        return left != null && right != null && left.getId() != null && left.getId().equals(right.getId());
    }

    private Map<String, Object> mergeSurgery(Map<String, Object> source, Map<String, Object> surgery) {
        Map<String, Object> merged = source == null
                ? new java.util.HashMap<>()
                : new java.util.HashMap<>(source);
        if (surgery == null) merged.remove("surgery");
        else merged.put("surgery", surgery);
        return merged.isEmpty() ? null : merged;
    }
}
