package com.vietnam.pji.controller.agentic;

import com.vietnam.pji.constant.TriggerType;
import com.vietnam.pji.dto.response.AiRecommendationRunDetailDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.services.agent.AiRecommendationService;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
@Tag(name = "AI Recommendations", description = "Generate, poll, and retry PJI AI recommendation runs (async via RabbitMQ)")
public class AiRecommendationController {

        private final AiRecommendationService aiRecommendationService;
        private final AiRecommendationStreamController streamController;

        @PostMapping("/episodes/{episodeId}/diagnostic-test/evaluate")
        @Operation(summary = "Evaluate PJI diagnostic test using backend rule engine only")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Rule-based diagnostic generated without calling RAG/AI"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
                        @ApiResponse(responseCode = "404", description = "Episode not found")
        })
        public ResponseData<PjiDiagnosticRuleEngine.DiagnosticResult> evaluateDiagnostic(
                        @PathVariable Long episodeId) {
                return new ResponseData<>(HttpStatus.OK.value(),
                                "Rule-based diagnostic generated successfully",
                                aiRecommendationService.evaluateRuleBasedDiagnostic(episodeId));
        }

        /**
         * Async: publishes to RabbitMQ, returns 202 with run in PROCESSING status.
         * Client polls GET /ai-recommendations/runs/{runId} for the result.
         */
        @PostMapping("/episodes/{episodeId}/ai-recommendations/generate")
        @ResponseStatus(HttpStatus.ACCEPTED)
        @Operation(summary = "Generate AI recommendation (async via RabbitMQ)")
        @ApiResponses({
                        @ApiResponse(responseCode = "202", description = "Job submitted; run is PROCESSING — poll GET /ai-recommendations/runs/{runId} for the result"),
                        @ApiResponse(responseCode = "400", description = "Per-episode run limit reached, or clinical state not eligible for generation"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
                        @ApiResponse(responseCode = "404", description = "Episode not found")
        })
        public ResponseData<AiRecommendationRunDetailDTO> generateRecommendation(@PathVariable Long episodeId) {
                return new ResponseData<>(HttpStatus.ACCEPTED.value(),
                                "Recommendation job submitted — poll GET /ai-recommendations/runs/{runId} for result",
                                aiRecommendationService.generateRecommendationAsync(episodeId,
                                                TriggerType.MANUAL_GENERATE));
        }

        /**
         * Sync fallback: calls AI service via HTTP, blocks until result.
         * Use this only when RabbitMQ is unavailable.
         */
        @PostMapping("/episodes/{episodeId}/ai-recommendations/generate-sync")
        @Operation(summary = "Generate AI recommendation (sync HTTP fallback)")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Recommendation generated synchronously"),
                        @ApiResponse(responseCode = "400", description = "Per-episode run limit reached, or clinical state not eligible for generation"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
                        @ApiResponse(responseCode = "404", description = "Episode not found"),
                        @ApiResponse(responseCode = "500", description = "AI service call failed or timed out")
        })
        public ResponseData<AiRecommendationRunDetailDTO> generateRecommendationSync(
                        @PathVariable Long episodeId) {
                return new ResponseData<>(HttpStatus.OK.value(), "Recommendation generated successfully",
                                aiRecommendationService.generateRecommendation(episodeId, TriggerType.MANUAL_GENERATE));
        }

        @GetMapping("/episodes/{episodeId}/ai-recommendations/runs")
        @Operation(summary = "Get AI recommendation run history for an episode")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Paginated run history"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
                        @ApiResponse(responseCode = "404", description = "Episode not found")
        })
        public ResponseData<PaginationResultDTO> getRunHistory(
                        @PathVariable Long episodeId, Pageable pageable) {
                return new ResponseData<>(HttpStatus.OK.value(), "Fetch run history successfully",
                                aiRecommendationService.getRunHistory(episodeId, pageable));
        }

        @GetMapping("/ai-recommendations/runs/{runId}")
        @Operation(summary = "Get detail of a specific AI recommendation run (poll for async result)")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Run detail with items and citations (status reflects async progress)"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
                        @ApiResponse(responseCode = "404", description = "Run not found")
        })
        public ResponseData<AiRecommendationRunDetailDTO> getRunDetail(@PathVariable Long runId) {
                return new ResponseData<>(HttpStatus.OK.value(), "Fetch run detail successfully",
                                aiRecommendationService.getRunDetail(runId));
        }

        @PostMapping("/ai-recommendations/runs/{runId}/retry")
        @ResponseStatus(HttpStatus.ACCEPTED)
        @Operation(summary = "Retry a failed AI recommendation run (async)")
        @ApiResponses({
                        @ApiResponse(responseCode = "202", description = "Retry job submitted"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
                        @ApiResponse(responseCode = "404", description = "Run not found")
        })
        public ResponseData<AiRecommendationRunDetailDTO> retryRun(@PathVariable Long runId) {
                return new ResponseData<>(HttpStatus.ACCEPTED.value(),
                                "Retry job submitted",
                                aiRecommendationService.retryRun(runId));
        }

        @PostMapping("/ai-recommendations/runs/{runId}/cancel")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Cancel an in-flight AI recommendation run")
        @ApiResponses({
                        @ApiResponse(responseCode = "204", description = "Run cancelled"),
                        @ApiResponse(responseCode = "400", description = "Run is not in a cancellable state (only QUEUED or PROCESSING can be cancelled)"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
                        @ApiResponse(responseCode = "403", description = "You can only cancel runs you started"),
                        @ApiResponse(responseCode = "404", description = "Run not found")
        })
        public ResponseData<Void> cancelRun(@PathVariable Long runId) {
                aiRecommendationService.cancelRun(runId);
                // Close the per-run SSE so any client still listening sees the
                // terminal event immediately instead of waiting for timeout.
                streamController.closeRun(runId, "CANCELLED");
                return new ResponseData<>(HttpStatus.NO_CONTENT.value(), "Run cancelled");
        }
}
