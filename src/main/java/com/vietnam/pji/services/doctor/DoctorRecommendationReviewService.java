package com.vietnam.pji.services.doctor;

import com.vietnam.pji.dto.request.DoctorRecommendationReviewRequestDTO;
import com.vietnam.pji.dto.request.PharmacistFinalDecisionRequestDTO;
import com.vietnam.pji.model.agentic.DoctorRecommendationReview;

import java.util.List;

public interface DoctorRecommendationReviewService {

    DoctorRecommendationReview createOrUpdateReview(Long episodeId, DoctorRecommendationReviewRequestDTO request);

    DoctorRecommendationReview getReviewByRunId(Long runId);

    List<DoctorRecommendationReview> getReviewsByEpisodeId(Long episodeId);

    DoctorRecommendationReview getFinalDecisionByEpisodeId(Long episodeId);

    DoctorRecommendationReview selectFinalDecision(Long episodeId, Long reviewId);

    DoctorRecommendationReview savePharmacistFinalDecision(
            Long reviewId,
            PharmacistFinalDecisionRequestDTO request);

    /**
     * Aggregate AI-vs-doctor consensus statistics: status counts, consensus
     * rate, average per-criterion agreement, and the cases where the doctor
     * overrode the AI.
     */
    com.vietnam.pji.dto.response.DoctorReviewStatsDTO getReviewStats();
}
