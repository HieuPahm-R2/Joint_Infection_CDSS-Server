package com.vietnam.pji.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.config.integration.RabbitMQConfig;
import com.vietnam.pji.constant.*;
import com.vietnam.pji.controller.agentic.AiRecommendationStreamController;
import com.vietnam.pji.dto.response.RabbitMQProgressMessage;
import com.vietnam.pji.dto.response.RabbitMQRecommendationResultMessage;
import com.vietnam.pji.model.agentic.*;
import com.vietnam.pji.repository.*;
import com.vietnam.pji.repository.ai.AiRagCitationRepository;
import com.vietnam.pji.repository.ai.AiRecommendationItemRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.feat.NotificationService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.services.medical.PendingLabTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Consumes AI processing results from the RabbitMQ result queue.
 * The Python RAG worker publishes results here after processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMQConsumer {

    private final AiRecommendationRunRepository runRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final AiRagCitationRepository citationRepository;
    private final ObjectMapper objectMapper;
    private final PendingLabTaskService pendingLabTaskService;
    private final RedisService redisService;
    private final AiRecommendationStreamController streamController;
    private final NotificationService notificationService;

    /**
     * Relay progress (thought-log) messages from the Python worker straight to
     * the SSE stream for the matching runId. Nothing is persisted.
     */
    @RabbitListener(queues = RabbitMQConfig.RECOMMENDATION_PROGRESS_QUEUE)
    public void handleRecommendationProgress(RabbitMQProgressMessage progress) {
        if (progress == null || progress.getRunId() == null) {
            log.debug("Dropping progress message without runId");
            return;
        }
        if (redisService.isRunCancelled(progress.getRunId())) {
            log.debug("Dropping progress for cancelled runId={}", progress.getRunId());
            streamController.closeRun(progress.getRunId(), "CANCELLED");
            return;
        }
        log.debug("Progress runId={} stage={} message={}",
                progress.getRunId(), progress.getStage(), progress.getMessage());
        streamController.pushProgress(
                progress.getRunId(), progress.getMessage(), progress.getStage());
    }

    @RabbitListener(queues = RabbitMQConfig.RECOMMENDATION_RESULT_QUEUE)
    @Transactional
    public void handleRecommendationResult(RabbitMQRecommendationResultMessage result) {
        String requestId = result.getRequestId();
        Long runId = result.getRunId();

        log.info("Received AI result from queue: requestId={}, runId={}, status={}",
                requestId, runId, result.getStatus());

        // Find the run — by runId first, fallback to requestId
        AiRecommendationRun run = null;
        if (runId != null) {
            run = runRepository.findByIdForUpdate(runId).orElse(null);
        }
        if (run == null && requestId != null) {
            AiRecommendationRun byRequestId = runRepository.findByRequestId(requestId).orElse(null);
            if (byRequestId != null) {
                run = runRepository.findByIdForUpdate(byRequestId.getId()).orElse(byRequestId);
            }
        }
        if (run == null) {
            log.error("Cannot find run for result: runId={}, requestId={}", runId, requestId);
            return;
        }

        if (dropIfCancellationRequested(run, "result received")) {
            return;
        }

        // Idempotency: skip if already succeeded
        if (run.getStatus() == RunStatus.SUCCESS) {
            log.info("Run {} already SUCCESS, skipping duplicate result", run.getId());
            streamController.closeRun(run.getId(), "SUCCESS");
            return;
        }

        // Handle FAILED status
        if ("FAILED".equals(result.getStatus())) {
            if (dropIfCancellationRequested(run, "before saving failed result")) {
                return;
            }
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(result.getErrorMessage() != null
                    ? result.getErrorMessage().substring(0, Math.min(result.getErrorMessage().length(), 2000))
                    : "AI processing failed");
            run.setLatencyMs(result.getLatencyMs());
            runRepository.save(run);
            log.warn("AI processing failed for runId={}: {}", run.getId(), result.getErrorMessage());
            streamController.closeRun(run.getId(), "FAILED");
            notifyRunFinished(run, false, run.getErrorMessage());
            return;
        }

        // Validate response has items
        if (result.getItems() == null || result.getItems().isEmpty()) {
            if (dropIfCancellationRequested(run, "before saving empty-result failure")) {
                return;
            }
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage("AI response missing required items");
            runRepository.save(run);
            log.warn("AI result has no items for runId={}", run.getId());
            streamController.closeRun(run.getId(), "FAILED");
            notifyRunFinished(run, false, "AI response missing required items");
            return;
        }

        if (dropIfCancellationRequested(run, "before saving AI result")) {
            return;
        }

        // Update run with AI results
        run.setStatus("SUCCESS".equals(result.getStatus()) ? RunStatus.SUCCESS : RunStatus.PARTIAL);
        run.setLatencyMs(result.getLatencyMs());

        if (result.getModel() != null) {
            run.setModelName(result.getModel().getName());
            run.setModelVersion(result.getModel().getVersion());
        }

        preserveRuleBasedDiagnosisRunFields(run, result.getAssessmentJson(),
                result.getExplanationJson(), result.getWarningsJson());

        // Store data completeness on the run for frontend display
        if (result.getDataCompleteness() != null) {
            run.setDataCompletenessJson(result.getDataCompleteness());
        }

        runRepository.save(run);

        if (dropIfCancellationRequested(run, "before saving dependent AI data")) {
            return;
        }

        // Auto-create pending lab tasks from completeness missing items
        createPendingTasksFromCompleteness(run, result.getDataCompleteness());

        if (dropIfCancellationRequested(run, "before saving recommendation items")) {
            return;
        }

        // Save items
        Map<String, AiRecommendationItem> itemKeyMap = new HashMap<>();

        for (RabbitMQRecommendationResultMessage.ItemDTO itemDTO : result.getItems()) {
            if (dropIfCancellationRequested(run, "during recommendation item save")) {
                return;
            }
            ItemCategory category = parseCategory(itemDTO.getCategory());
            if (category == ItemCategory.DIAGNOSTIC_TEST) {
                log.debug("Skipping AI DIAGNOSTIC_TEST item for runId={} because backend rule engine owns diagnosis",
                        run.getId());
                continue;
            }

            AiRecommendationItem item = AiRecommendationItem.builder()
                    .run(run)
                    .category(category)
                    .title(itemDTO.getTitle())
                    .priorityOrder(itemDTO.getPriorityOrder())
                    .isPrimary(itemDTO.getIsPrimary())
                    .build();

            try {
                if (itemDTO.getItemJson() != null) {
                    item.setItemJson(objectMapper.writeValueAsString(itemDTO.getItemJson()));
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize item_json for category={}", itemDTO.getCategory());
            }

            AiRecommendationItem saved = itemRepository.save(item);
            if (itemDTO.getClientItemKey() != null) {
                itemKeyMap.put(itemDTO.getClientItemKey(), saved);
            }
        }

        if (dropIfCancellationRequested(run, "before saving citations")) {
            return;
        }

        // Save citations
        if (result.getCitations() != null) {
            for (RabbitMQRecommendationResultMessage.CitationDTO citDTO : result.getCitations()) {
                if (dropIfCancellationRequested(run, "during citation save")) {
                    return;
                }
                if (citDTO.getClientItemKey() != null && !itemKeyMap.containsKey(citDTO.getClientItemKey())) {
                    log.debug("Skipping citation for unmapped AI item key={} on runId={}",
                            citDTO.getClientItemKey(), run.getId());
                    continue;
                }

                AiRagCitation citation = AiRagCitation.builder()
                        .run(run)
                        .sourceType(parseSourceType(citDTO.getSourceType()))
                        .sourceTitle(citDTO.getSourceTitle())
                        .sourceUri(citDTO.getSourceUri())
                        .snippet(citDTO.getSnippet())
                        .relevanceScore(citDTO.getRelevanceScore())
                        .citedFor(citDTO.getCitedFor())
                        .build();

                if (citDTO.getClientItemKey() != null && itemKeyMap.containsKey(citDTO.getClientItemKey())) {
                    citation.setItem(itemKeyMap.get(citDTO.getClientItemKey()));
                }

                citationRepository.save(citation);
            }
        }

        if (dropIfCancellationRequested(run, "before finalizing AI result")) {
            return;
        }

        // Evict run detail cache so next poll gets fresh data
        redisService.evictRunDetail(run.getId());

        log.info("Successfully saved AI result for runId={}: {} items, {} citations",
                run.getId(),
                result.getItems().size(),
                result.getCitations() != null ? result.getCitations().size() : 0);

        streamController.closeRun(run.getId(), run.getStatus().name());
        notifyRunFinished(run, run.getStatus() != RunStatus.FAILED, null);
    }

    private boolean dropIfCancellationRequested(AiRecommendationRun run, String phase) {
        if (!isCancellationRequested(run)) {
            return false;
        }
        if (run.getStatus() != RunStatus.CANCELLED) {
            run.setStatus(RunStatus.CANCELLED);
            run.setErrorMessage("Cancelled by user");
            runRepository.save(run);
        }
        redisService.evictRunDetail(run.getId());
        log.info("Run {} cancellation observed at '{}' — dropping worker result", run.getId(), phase);
        streamController.closeRun(run.getId(), "CANCELLED");
        return true;
    }

    private boolean isCancellationRequested(AiRecommendationRun run) {
        return run.getStatus() == RunStatus.CANCELLED || redisService.isRunCancelled(run.getId());
    }

    private void preserveRuleBasedDiagnosisRunFields(
            AiRecommendationRun run,
            Map<String, Object> aiAssessmentJson,
            Map<String, Object> aiExplanationJson,
            List<Map<String, Object>> aiWarningsJson) {
        if (run.getAssessmentJson() == null || run.getAssessmentJson().isEmpty()) {
            run.setAssessmentJson(aiAssessmentJson);
        }
        if (run.getExplanationJson() == null || run.getExplanationJson().isEmpty()) {
            run.setExplanationJson(aiExplanationJson);
        }
        if (run.getWarningsJson() == null || run.getWarningsJson().isEmpty()) {
            run.setWarningsJson(aiWarningsJson);
        }
    }

    /**
     * Create a Notification row for the user who started the run, so the badge
     * + dropdown in the UI can show it even after they navigated away. The
     * NotificationService also fans it out over the per-user SSE stream.
     */
    private void notifyRunFinished(AiRecommendationRun run, boolean success, String errorMessage) {
        Long userId = run.getCreatedByUserId();
        if (userId == null) {
            log.debug("Run {} has no createdByUserId — skipping notification", run.getId());
            return;
        }

        Long episodeId = null;
        try {
            episodeId = run.getEpisode() != null ? run.getEpisode().getId() : null;
        } catch (Exception e) {
            log.debug("Failed to read episode id for run {}: {}", run.getId(), e.getMessage());
        }

        String linkUrl = "/?runId=" + run.getId()
                + (episodeId != null ? "&episodeId=" + episodeId : "");
        try {
            if (success) {
                notificationService.create(
                        userId,
                        NotificationType.AI_RECOMMENDATION_DONE,
                        NotificationSeverity.SUCCESS,
                        "Phân tích PJI hoàn tất",
                        "Khuyến nghị AI cho ca lâm sàng đã sẵn sàng để xem.",
                        String.valueOf(run.getId()),
                        linkUrl);
            } else {
                String msg = errorMessage != null && !errorMessage.isBlank()
                        ? errorMessage
                        : "Phân tích AI thất bại. Vui lòng thử lại.";
                notificationService.create(
                        userId,
                        NotificationType.AI_RECOMMENDATION_FAILED,
                        NotificationSeverity.ERROR,
                        "Phân tích PJI thất bại",
                        msg,
                        String.valueOf(run.getId()),
                        linkUrl);
            }
        } catch (Exception e) {
            // Notification failure must not break the result-saving transaction.
            log.warn("Failed to create notification for runId={}: {}", run.getId(), e.getMessage());
        }
    }

    private ItemCategory parseCategory(String category) {
        try {
            return ItemCategory.valueOf(category);
        } catch (Exception e) {
            log.warn("Unknown item category: {}", category);
            return ItemCategory.DIAGNOSTIC_TEST;
        }
    }

    private SourceType parseSourceType(String sourceType) {
        try {
            return SourceType.valueOf(sourceType);
        } catch (Exception e) {
            log.warn("Unknown source type: {}", sourceType);
            return SourceType.GUIDELINE;
        }
    }

    @SuppressWarnings("unchecked")
    private void createPendingTasksFromCompleteness(AiRecommendationRun run,
            Map<String, Object> dataCompleteness) {
        if (dataCompleteness == null)
            return;

        List<Map<String, Object>> missingItems = (List<Map<String, Object>>) dataCompleteness.get("missing_items");
        if (missingItems == null || missingItems.isEmpty())
            return;

        try {
            Long episodeId = run.getEpisode().getId();
            Long patientId = run.getEpisode().getPatient() != null
                    ? run.getEpisode().getPatient().getId()
                    : null;
            // Assign to the user who created this run
            Long userId = null;
            if (run.getCreatedBy() != null) {
                // createdBy stores the email; we pass null for now — the controller
                // endpoint for manual creation handles userId. For async runs the
                // frontend will call the create-from-completeness endpoint with the
                // logged-in user, so we keep this as a fallback.
                userId = null;
            }

            pendingLabTaskService.createFromCompleteness(
                    episodeId, patientId, userId, run.getId(), missingItems);
        } catch (Exception e) {
            log.warn("Failed to create pending tasks from completeness for runId={}: {}",
                    run.getId(), e.getMessage());
        }
    }
}
