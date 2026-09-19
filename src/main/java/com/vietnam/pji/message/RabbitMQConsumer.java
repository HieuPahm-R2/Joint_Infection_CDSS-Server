package com.vietnam.pji.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.config.integration.RabbitMQConfig;
import com.vietnam.pji.constant.*;
import com.vietnam.pji.controller.agentic.AiRecommendationStreamController;
import com.vietnam.pji.dto.response.RabbitMQProgressMessage;
import com.vietnam.pji.dto.response.RabbitMQRecommendationResultMessage;
import com.vietnam.pji.model.agentic.*;
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

        // First terminal result wins. Outbox delivery is at-least-once, so a
        // duplicate worker result must not append items or overwrite the outcome.
        if (isTerminalResultStatus(run.getStatus())) {
            log.info("Run {} already {}, skipping duplicate result", run.getId(), run.getStatus());
            streamController.closeRun(run.getId(), run.getStatus().name());
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

        RecommendationScope scope = run.getRecommendationScope();
        List<ItemCategory> receivedCategories = result.getItems().stream()
                .map(item -> parseCategory(item.getCategory()))
                .filter(Objects::nonNull)
                .toList();
        if (receivedCategories.size() != result.getItems().size()
                || receivedCategories.size() != scope.requiredItemCategories().size()
                || !new HashSet<>(receivedCategories).equals(scope.requiredItemCategories())) {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage("AI response categories do not match recommendation scope " + scope.name());
            runRepository.save(run);
            streamController.closeRun(run.getId(), "FAILED");
            notifyRunFinished(run, false, run.getErrorMessage());
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
            if (category == null) {
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
        String patientName = "Bệnh nhân";
        String medicalRecordCode = null;
        try {
            if (run.getEpisode() != null) {
                episodeId = run.getEpisode().getId();
                medicalRecordCode = run.getEpisode().getMedicalRecordCode();
                if (run.getEpisode().getPatient() != null && run.getEpisode().getPatient().getFullName() != null) {
                    patientName = run.getEpisode().getPatient().getFullName();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to read episode/patient info for run {}: {}", run.getId(), e.getMessage());
        }

        String recordDisplay = medicalRecordCode != null && !medicalRecordCode.isBlank()
                ? medicalRecordCode
                : (episodeId != null ? "#" + episodeId : "");
        String linkUrl = buildRecommendationLink(run, episodeId);
        try {
            if (success) {
                String title = "Phân tích PJI hoàn tất - " + patientName;
                String message = "Khuyến nghị phác đồ AI cho bệnh nhân " + patientName
                        + (!recordDisplay.isBlank() ? " (Bệnh án " + recordDisplay + ")" : "")
                        + " đã hoàn tất và sẵn sàng để xem.";
                notificationService.create(
                        userId,
                        NotificationType.AI_RECOMMENDATION_DONE,
                        NotificationSeverity.SUCCESS,
                        title,
                        message,
                        String.valueOf(run.getId()),
                        linkUrl);
            } else {
                String msg = errorMessage != null && !errorMessage.isBlank()
                        ? errorMessage
                        : "Phân tích AI thất bại. Vui lòng thử lại.";
                String title = "Phân tích PJI thất bại - " + patientName;
                String message = "Quá trình sinh phác đồ AI cho bệnh nhân " + patientName
                        + (!recordDisplay.isBlank() ? " (Bệnh án " + recordDisplay + ")" : "")
                        + " thất bại: " + msg;
                notificationService.create(
                        userId,
                        NotificationType.AI_RECOMMENDATION_FAILED,
                        NotificationSeverity.ERROR,
                        title,
                        message,
                        String.valueOf(run.getId()),
                        linkUrl);
            }
        } catch (Exception e) {
            // Notification failure must not break the result-saving transaction.
            log.warn("Failed to create notification for runId={}: {}", run.getId(), e.getMessage());
        }
    }

    static String buildRecommendationLink(AiRecommendationRun run, Long episodeId) {
        String pathname = run.getRecommendationScope() == RecommendationScope.ANTIBIOTIC
                ? "/antibiotic-planner"
                : "/";
        return pathname + "?runId=" + run.getId()
                + (episodeId != null ? "&episodeId=" + episodeId : "");
    }

    static boolean isTerminalResultStatus(RunStatus status) {
        return status == RunStatus.SUCCESS
                || status == RunStatus.PARTIAL
                || status == RunStatus.FAILED
                || status == RunStatus.TIMEOUT
                || status == RunStatus.CANCELLED;
    }

    private ItemCategory parseCategory(String category) {
        try {
            return ItemCategory.valueOf(category);
        } catch (Exception e) {
            log.warn("Ignoring unsupported recommendation item category: {}", category);
            return null;
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
