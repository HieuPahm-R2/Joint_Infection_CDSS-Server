package com.vietnam.pji.controller.agentic;

import com.vietnam.pji.dto.request.DoctorRecommendationReviewRequestDTO;
import com.vietnam.pji.dto.request.PharmacistFinalDecisionRequestDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.agentic.DoctorRecommendationReview;
import com.vietnam.pji.services.doctor.DoctorRecommendationReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${api.prefix}")
@Validated
@RequiredArgsConstructor
@Tag(name = "Doctor Reviews", description = "Doctor review and feedback on AI recommendation runs")
public class DoctorRecommendationReviewController {

    private final DoctorRecommendationReviewService reviewService;

    @PostMapping("/episodes/{episodeId}/doctor-reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or update a doctor review for an AI recommendation run")
    public ResponseData<DoctorRecommendationReview> createReview(
            @PathVariable Long episodeId,
            @Valid @RequestBody DoctorRecommendationReviewRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(),
                "Doctor review saved successfully",
                reviewService.createOrUpdateReview(episodeId, request));
    }

    @GetMapping("/ai-recommendations/runs/{runId}/review")
    @Operation(summary = "Get doctor review for a specific AI recommendation run")
    public ResponseData<DoctorRecommendationReview> getReviewByRunId(@PathVariable Long runId) {
        return new ResponseData<>(HttpStatus.OK.value(),
                "Fetch review successfully",
                reviewService.getReviewByRunId(runId));
    }

    @GetMapping("/episodes/{episodeId}/doctor-reviews")
    @Operation(summary = "Get all doctor reviews for an episode")
    public ResponseData<List<DoctorRecommendationReview>> getReviewsByEpisode(@PathVariable Long episodeId) {
        return new ResponseData<>(HttpStatus.OK.value(),
                "Fetch reviews successfully",
                reviewService.getReviewsByEpisodeId(episodeId));
    }

    @GetMapping("/episodes/{episodeId}/doctor-reviews/final-decision")
    @Operation(summary = "Get the selected final recommendation version for an episode")
    public ResponseData<DoctorRecommendationReview> getFinalDecision(@PathVariable Long episodeId) {
        return new ResponseData<>(HttpStatus.OK.value(),
                "Fetch final decision successfully",
                reviewService.getFinalDecisionByEpisodeId(episodeId));
    }

    @PutMapping("/episodes/{episodeId}/doctor-reviews/{reviewId}/final-decision")
    @Operation(summary = "Select one reviewed recommendation version as final")
    public ResponseData<DoctorRecommendationReview> selectFinalDecision(
            @PathVariable Long episodeId,
            @PathVariable Long reviewId) {
        return new ResponseData<>(HttpStatus.OK.value(),
                "Final decision version selected successfully",
                reviewService.selectFinalDecision(episodeId, reviewId));
    }

    @PutMapping("/doctor-reviews/{reviewId}/pharmacist-final-decision")
    @Operation(summary = "Create or update the pharmacist decision for a review version")
    public ResponseData<DoctorRecommendationReview> savePharmacistFinalDecision(
            @PathVariable Long reviewId,
            @Valid @RequestBody PharmacistFinalDecisionRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(),
                "Pharmacist final decision saved successfully",
                reviewService.savePharmacistFinalDecision(reviewId, request));
    }

    @GetMapping("/doctor-reviews/stats")
    @Operation(summary = "AI-vs-doctor consensus statistics",
            description = "Status counts, consensus rate, average per-criterion agreement, and cases where the doctor overrode the AI")
    public ResponseData<com.vietnam.pji.dto.response.DoctorReviewStatsDTO> getReviewStats() {
        return new ResponseData<>(HttpStatus.OK.value(),
                "Fetch review stats successfully",
                reviewService.getReviewStats());
    }
}
