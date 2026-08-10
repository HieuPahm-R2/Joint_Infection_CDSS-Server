package com.vietnam.pji.services.doctor.impl;

import com.vietnam.pji.constant.ReviewStatus;
import com.vietnam.pji.dto.request.DoctorRecommendationReviewRequestDTO;
import com.vietnam.pji.dto.request.DoctorFinalDecisionRequestDTO;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.DoctorRecommendationReview;
import com.vietnam.pji.model.agentic.DoctorFinalDecision;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.DoctorRecommendationReviewRepository;
import com.vietnam.pji.repository.DoctorFinalDecisionRepository;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.services.doctor.DoctorRecommendationReviewService;
import com.vietnam.pji.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorRecommendationReviewServiceImpl implements DoctorRecommendationReviewService {

    private final DoctorRecommendationReviewRepository reviewRepository;
    private final DoctorFinalDecisionRepository doctorFinalDecisionRepository;
    private final AiRecommendationRunRepository runRepository;
    private final EpisodeRepository episodeRepository;
    private final UserService userService;

    @Override
    @Transactional
    public DoctorRecommendationReview createOrUpdateReview(Long episodeId,
            DoctorRecommendationReviewRequestDTO request) {
        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found with id: " + episodeId));

        AiRecommendationRun run = runRepository.findById(request.getRunId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AI Recommendation Run not found with id: " + request.getRunId()));

        validateReviewAccess(episode, run);

        ReviewStatus status = ReviewStatus.valueOf(request.getReviewStatus());

        // Upsert: update existing review for this run, or create new
        DoctorRecommendationReview review = reviewRepository.findByRunId(request.getRunId())
                .orElse(DoctorRecommendationReview.builder()
                        .episode(episode)
                        .run(run)
                        .build());

        review.setReviewStatus(status);
        review.setReviewNote(request.getReviewNote());
        review.setRejectionReason(request.getRejectionReason());

        review.setModificationJson(request.getModificationJson());
        review.setDoctorDiagnosisJson(request.getDoctorDiagnosisJson());
        review.setAgreementJson(request.getAgreementJson());

        DoctorRecommendationReview saved = reviewRepository.save(review);
        upsertDoctorFinalDecision(saved, request);
        if (Boolean.TRUE.equals(request.getSelectAsFinalDecision())) {
            saved = selectFinalDecision(episodeId, saved.getId());
        }
        eagerInit(saved);
        return saved;
    }

    private void upsertDoctorFinalDecision(
            DoctorRecommendationReview review,
            DoctorRecommendationReviewRequestDTO request) {
        DoctorFinalDecisionRequestDTO decisionRequest = request.getDoctorFinalDecision();
        Map<String, Object> diagnosis = decisionRequest != null
                ? decisionRequest.getDiagnosisJson()
                : request.getDoctorDiagnosisJson();
        Map<String, Object> surgery = decisionRequest != null
                ? decisionRequest.getSurgeryPlanJson()
                : extractLegacyPlan(request.getModificationJson(), "surgery");

        if (diagnosis == null && surgery == null) {
            return;
        }

        DoctorFinalDecision decision = doctorFinalDecisionRepository.findByReviewId(review.getId())
                .orElse(DoctorFinalDecision.builder().review(review).build());
        decision.setDiagnosisJson(diagnosis != null ? diagnosis : Map.of());
        decision.setSurgeryPlanJson(surgery);
        DoctorFinalDecision saved = doctorFinalDecisionRepository.save(decision);
        review.setDoctorFinalDecision(saved);

        // Retain legacy response fields while clients migrate to the new entities.
        review.setDoctorDiagnosisJson(saved.getDiagnosisJson());
        review.setModificationJson(mergeLegacyPlan(review.getModificationJson(), "surgery", surgery));
        reviewRepository.save(review);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractLegacyPlan(Map<String, Object> source, String key) {
        Object value = source != null ? source.get(key) : null;
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    private Map<String, Object> mergeLegacyPlan(
            Map<String, Object> source,
            String key,
            Map<String, Object> value) {
        Map<String, Object> merged = source != null ? new HashMap<>(source) : new HashMap<>();
        if (value != null) {
            merged.put(key, value);
        } else {
            merged.remove(key);
        }
        return merged.isEmpty() ? null : merged;
    }

    private void validateReviewAccess(PjiEpisode episode, AiRecommendationRun run) {
        if (run.getEpisode() == null || !episode.getId().equals(run.getEpisode().getId())) {
            throw new ForbiddenException("AI recommendation run does not belong to this episode");
        }

        String currentEmail = SecurityUtils.getCurrentUserLogin().orElse("");
        if (isBlank(currentEmail)) {
            throw new ForbiddenException("You don't have permission to review this treatment plan");
        }

        if (isAdmin(currentEmail)) {
            return;
        }

        String patientCreatedBy = episode.getPatient() != null ? episode.getPatient().getCreatedBy() : null;
        String episodeCreatedBy = episode.getCreatedBy();
        String runCreatedBy = run.getCreatedBy();
        boolean hasOwnerMetadata = !isBlank(patientCreatedBy) || !isBlank(episodeCreatedBy) || !isBlank(runCreatedBy);

        if (hasOwnerMetadata
                && !sameUser(currentEmail, patientCreatedBy)
                && !sameUser(currentEmail, episodeCreatedBy)
                && !sameUser(currentEmail, runCreatedBy)) {
            throw new ForbiddenException("Only the owner of this medical record can review this treatment plan");
        }
    }

    private boolean isAdmin(String email) {
        User user = userService.handleGetUserByUsername(email);
        String roleName = user != null && user.getRole() != null ? user.getRole().getName() : "";
        return "ADMIN".equalsIgnoreCase(roleName) || "SUPER_ADMIN".equalsIgnoreCase(roleName);
    }

    private boolean sameUser(String currentEmail, String ownerEmail) {
        return !isBlank(ownerEmail) && currentEmail.trim().equalsIgnoreCase(ownerEmail.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorRecommendationReview getReviewByRunId(Long runId) {
        DoctorRecommendationReview review = reviewRepository.findByRunId(runId).orElse(null);
        if (review != null) {
            eagerInit(review);
        }
        return review;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorRecommendationReview> getReviewsByEpisodeId(Long episodeId) {
        List<DoctorRecommendationReview> reviews = reviewRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
        reviews.forEach(this::eagerInit);
        return reviews;
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorRecommendationReview getFinalDecisionByEpisodeId(Long episodeId) {
        DoctorRecommendationReview review = reviewRepository
                .findByEpisodeIdAndFinalDecisionTrue(episodeId)
                .orElse(null);
        if (review != null) {
            eagerInit(review);
        }
        return review;
    }

    @Override
    @Transactional
    public DoctorRecommendationReview selectFinalDecision(Long episodeId, Long reviewId) {
        DoctorRecommendationReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor review not found with id: " + reviewId));
        if (review.getEpisode() == null || !episodeId.equals(review.getEpisode().getId())) {
            throw new ForbiddenException("Doctor review does not belong to this episode");
        }
        validateReviewAccess(review.getEpisode(), review.getRun());

        reviewRepository.clearFinalDecisionForEpisode(episodeId);
        review.setFinalDecision(true);
        DoctorRecommendationReview saved = reviewRepository.save(review);
        eagerInit(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public com.vietnam.pji.dto.response.DoctorReviewStatsDTO getReviewStats() {
        List<DoctorRecommendationReview> reviews = reviewRepository.findAll();

        long accepted = countByStatus(reviews, ReviewStatus.ACCEPTED);
        long modified = countByStatus(reviews, ReviewStatus.MODIFIED);
        long rejected = countByStatus(reviews, ReviewStatus.REJECTED);
        long savedDraft = countByStatus(reviews, ReviewStatus.SAVED_DRAFT);
        long decided = accepted + modified + rejected;

        Double consensusRate = decided > 0
                ? Math.round(accepted * 1000.0 / decided) / 10.0
                : null;

        // Average per-criterion agreement across reviews that carry one.
        double sum = 0;
        int withAgreement = 0;
        for (DoctorRecommendationReview r : reviews) {
            Double rate = extractAgreementRate(r);
            if (rate != null) {
                sum += rate;
                withAgreement++;
            }
        }
        Double avgAgreementRate = withAgreement > 0
                ? Math.round(sum * 10.0 / withAgreement) / 10.0
                : null;

        List<com.vietnam.pji.dto.response.DoctorReviewStatsDTO.OverriddenCaseDTO> overridden = reviews.stream()
                .filter(r -> r.getReviewStatus() == ReviewStatus.MODIFIED
                        || r.getReviewStatus() == ReviewStatus.REJECTED)
                .sorted((a, b) -> {
                    java.util.Date ua = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                    java.util.Date ub = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
                    if (ua == null || ub == null)
                        return 0;
                    return ub.compareTo(ua);
                })
                .map(r -> com.vietnam.pji.dto.response.DoctorReviewStatsDTO.OverriddenCaseDTO.builder()
                        .reviewId(r.getId())
                        .episodeId(r.getEpisode() != null ? r.getEpisode().getId() : null)
                        .runId(r.getRun() != null ? r.getRun().getId() : null)
                        .patientName(r.getEpisode() != null && r.getEpisode().getPatient() != null
                                ? r.getEpisode().getPatient().getFullName()
                                : null)
                        .reviewStatus(r.getReviewStatus() != null ? r.getReviewStatus().name() : null)
                        .agreementRate(extractAgreementRate(r))
                        .reviewNote(r.getReviewNote())
                        .updatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt())
                        .build())
                .toList();

        return com.vietnam.pji.dto.response.DoctorReviewStatsDTO.builder()
                .totalReviews(reviews.size())
                .accepted(accepted)
                .modified(modified)
                .rejected(rejected)
                .savedDraft(savedDraft)
                .consensusRate(consensusRate)
                .avgAgreementRate(avgAgreementRate)
                .overriddenCases(overridden)
                .build();
    }

    private long countByStatus(List<DoctorRecommendationReview> reviews, ReviewStatus status) {
        return reviews.stream().filter(r -> r.getReviewStatus() == status).count();
    }

    private Double extractAgreementRate(DoctorRecommendationReview review) {
        if (review.getAgreementJson() == null)
            return null;
        Object rate = review.getAgreementJson().get("agreement_rate");
        if (rate instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private void eagerInit(DoctorRecommendationReview review) {
        Hibernate.initialize(review.getEpisode());
        if (review.getEpisode() != null) {
            Hibernate.initialize(review.getEpisode().getPatient());
        }
        Hibernate.initialize(review.getRun());
        Hibernate.initialize(review.getDoctorFinalDecision());
        if (review.getRun() != null) {
            Hibernate.initialize(review.getRun().getEpisode());
            if (review.getRun().getEpisode() != null) {
                Hibernate.initialize(review.getRun().getEpisode().getPatient());
            }
            Hibernate.initialize(review.getRun().getSnapshot());
            if (review.getRun().getSnapshot() != null) {
                Hibernate.initialize(review.getRun().getSnapshot().getEpisode());
            }
        }
    }
}
