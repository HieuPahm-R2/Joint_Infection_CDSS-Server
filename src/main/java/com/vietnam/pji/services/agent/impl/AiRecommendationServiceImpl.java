package com.vietnam.pji.services.agent.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.*;
import com.vietnam.pji.dto.request.AiRecommendationGenerateRequestDTO;
import com.vietnam.pji.dto.response.AiRecommendationGenerateResponseDTO;
import com.vietnam.pji.dto.response.AiRecommendationRunDetailDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.BusinessException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.*;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.*;
import com.vietnam.pji.repository.ai.AiRagCitationRepository;
import com.vietnam.pji.repository.ai.AiRecommendationItemRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.repository.ai.RuleBasedDiagnosticResultRepository;
import com.vietnam.pji.dto.request.RuleBasedDiagnosisDTO;
import com.vietnam.pji.services.agent.AiRecommendationService;
import com.vietnam.pji.services.agent.AiServiceClient;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService.SnapshotBuildResult;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.SecurityUtils;
import com.vietnam.pji.utils.mapper.AiRecommendationRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendationServiceImpl implements AiRecommendationService {

    private static final long RUN_DETAIL_CACHE_TTL = 1800; // 30 minutes

    private final EpisodeRepository episodeRepository;
    private final AiRecommendationRunRepository runRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final AiRagCitationRepository citationRepository;
    private final RuleBasedDiagnosticResultRepository diagnosticResultRepository;
    private final EpisodeSnapshotAssemblerService snapshotAssemblerService;
    private final AiServiceClient aiServiceClient;
    private final PjiDiagnosticRuleEngine diagnosticRuleEngine;
    private final ObjectMapper objectMapper;
    private final RedisService redisService;
    private final AiRecommendationRunMapper runMapper;
    private final RecommendationAccessService recommendationAccessService;
    private final RecommendationRunCreator recommendationRunCreator;

    @Override
    public AiRecommendationRunDetailDTO generateRecommendation(Long episodeId, TriggerType triggerType,
            RecommendationScope recommendationScope) {
        recommendationAccessService.assertCanGenerateEpisode(episodeId, recommendationScope);

        // Create durable input in a short transaction before calling AI.
        SnapshotBuildResult buildResult = snapshotAssemblerService.buildSnapshot(episodeId);
        PjiDiagnosticRuleEngine.DiagnosticResult diagnostic =
                diagnosticRuleEngine.evaluate(buildResult.getSnapshotDataJson());
        RecommendationRunCreator.CreatedRecommendationRun created = recommendationRunCreator.create(
                episodeId,
                buildResult,
                diagnostic,
                triggerType,
                recommendationScope,
                SecurityUtils.getCurrentUserId());
        CaseClinicalSnapshot snapshot = created.snapshot();
        AiRecommendationRun run = created.run();

        String requestId = run.getRequestId();

        // Call AI outside transaction
        AiRecommendationGenerateResponseDTO aiResponse;
        try {
            AiRecommendationGenerateRequestDTO request = AiRecommendationGenerateRequestDTO.builder()
                    .requestId(requestId)
                    .triggerType(triggerType.name())
                    .recommendationScope(recommendationScope.name())
                    .episodeId(episodeId)
                    .snapshotId(snapshot.getId())
                    .snapshotDataJson(buildResult.getSnapshotDataJson())
                    .ruleBasedDiagnosis(RuleBasedDiagnosisDTO.from(diagnostic))
                    .options(AiRecommendationGenerateRequestDTO.Options.builder().build())
                    .build();

            aiResponse = aiServiceClient.generateRecommendation(request);
        } catch (Exception e) {
            log.error("AI service call failed for requestId={}", requestId, e);
            handleAiError(run.getId(), e);
            throw new RuntimeException("AI service call failed: " + e.getMessage(), e);
        }

        // TX2: Save results
        return saveAiResults(run.getId(), aiResponse);
    }

    @Override
    public AiRecommendationRunDetailDTO generateRecommendationAsync(Long episodeId, TriggerType triggerType,
            RecommendationScope recommendationScope) {
        recommendationAccessService.assertCanGenerateEpisode(episodeId, recommendationScope);

        // Create durable input in a short transaction before publishing the job.
        SnapshotBuildResult buildResult = snapshotAssemblerService.buildSnapshot(episodeId);
        PjiDiagnosticRuleEngine.DiagnosticResult diagnostic =
                diagnosticRuleEngine.evaluate(buildResult.getSnapshotDataJson());
        RecommendationRunCreator.CreatedRecommendationRun created = recommendationRunCreator.createAsync(
                episodeId,
                buildResult,
                diagnostic,
                triggerType,
                recommendationScope,
                SecurityUtils.getCurrentUserId());
        CaseClinicalSnapshot snapshot = created.snapshot();
        AiRecommendationRun run = created.run();

        log.info("Persisted async recommendation job: requestId={}, runId={}, episodeId={}",
                run.getRequestId(), run.getId(), episodeId);

        // Return immediately with PROCESSING status — client polls GET /runs/{runId}
        return toRunDetailDto(run, Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public PjiDiagnosticRuleEngine.DiagnosticResult evaluateRuleBasedDiagnostic(Long episodeId) {
        recommendationAccessService.assertCanReviewEpisode(episodeId);
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode not found: " + episodeId);
        }
        SnapshotBuildResult buildResult = snapshotAssemblerService.buildSnapshot(episodeId);
        return diagnosticRuleEngine.evaluate(buildResult.getSnapshotDataJson());
    }

    private void handleAiError(Long runId, Exception e) {
        AiRecommendationRun run = runRepository.findById(runId).orElse(null);
        if (run != null) {
            boolean isTimeout = e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout");
            run.setStatus(isTimeout ? RunStatus.TIMEOUT : RunStatus.FAILED);
            run.setErrorMessage(
                    e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 2000))
                            : "Unknown error");
            runRepository.save(run);
        }
    }

    @Transactional
    protected AiRecommendationRunDetailDTO saveAiResults(Long runId, AiRecommendationGenerateResponseDTO response) {
        AiRecommendationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));

        // Validate response
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage("AI response missing required items");
            runRepository.save(run);
            throw new RuntimeException("AI response validation failed: missing items");
        }

        RecommendationScope scope = run.getRecommendationScope() == null
                ? RecommendationScope.LEGACY_COMBINED
                : run.getRecommendationScope();
        List<ItemCategory> receivedCategories = response.getItems().stream()
                .map(item -> parseCategory(item.getCategory()))
                .filter(Objects::nonNull)
                .toList();
        if (receivedCategories.size() != response.getItems().size()
                || receivedCategories.size() != scope.requiredItemCategories().size()
                || !new HashSet<>(receivedCategories).equals(scope.requiredItemCategories())) {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage("AI response categories do not match recommendation scope " + scope.name());
            runRepository.save(run);
            throw new BusinessException(run.getErrorMessage());
        }

        // Update run
        run.setStatus("SUCCESS".equals(response.getStatus()) ? RunStatus.SUCCESS : RunStatus.PARTIAL);
        if (response.getModel() != null) {
            run.setModelName(response.getModel().getName());
            run.setModelVersion(response.getModel().getVersion());
        }
        run.setLatencyMs(response.getLatencyMs());
        runRepository.save(run);

        // Save items
        Map<String, AiRecommendationItem> itemKeyMap = new HashMap<>();
        List<AiRecommendationItem> savedItems = new ArrayList<>();

        for (AiRecommendationGenerateResponseDTO.ItemDTO itemDTO : response.getItems()) {
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
            savedItems.add(saved);

            if (itemDTO.getClientItemKey() != null) {
                itemKeyMap.put(itemDTO.getClientItemKey(), saved);
            }
        }

        // Save citations
        List<AiRagCitation> savedCitations = new ArrayList<>();
        if (response.getCitations() != null) {
            for (AiRecommendationGenerateResponseDTO.CitationDTO citDTO : response.getCitations()) {
                if (citDTO.getClientItemKey() != null && !itemKeyMap.containsKey(citDTO.getClientItemKey())) {
                    log.debug("Skipping citation for unmapped AI item key={} on runId={}",
                            citDTO.getClientItemKey(), runId);
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

                // Map citation to item via client_item_key
                if (citDTO.getClientItemKey() != null && itemKeyMap.containsKey(citDTO.getClientItemKey())) {
                    citation.setItem(itemKeyMap.get(citDTO.getClientItemKey()));
                }

                savedCitations.add(citationRepository.save(citation));
            }
        }

        List<AiRecommendationItem> allItems = itemRepository.findByRunIdOrderByPriorityOrderAsc(runId);
        List<AiRagCitation> allCitations = citationRepository.findByRunId(runId);

        return toRunDetailDto(run, allItems, allCitations);
    }

    @Override
    @Transactional(readOnly = true)
    public AiRecommendationRunDetailDTO getRunDetail(Long runId) {
        recommendationAccessService.assertCanReviewRun(runId);
        // Check cache for terminal runs
        try {
            String cached = redisService.getCachedRunDetail(runId);
            if (cached != null) {
                log.debug("Run detail cache hit for runId={}", runId);
                return objectMapper.readValue(cached, AiRecommendationRunDetailDTO.class);
            }
        } catch (Exception e) {
            log.warn("Failed to read run detail cache for runId={}, loading from DB", runId);
        }

        AiRecommendationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));

        List<AiRecommendationItem> items = itemRepository.findByRunIdOrderByPriorityOrderAsc(runId);
        List<AiRagCitation> citations = citationRepository.findByRunId(runId);

        AiRecommendationRunDetailDTO detail = toRunDetailDto(run, items, citations);

        // Only cache terminal statuses (immutable data)
        if (isTerminalStatus(run.getStatus())) {
            try {
                redisService.cacheRunDetail(runId, objectMapper.writeValueAsString(detail), RUN_DETAIL_CACHE_TTL);
                log.debug("Run detail cached for runId={}", runId);
            } catch (Exception e) {
                log.warn("Failed to cache run detail for runId={}", runId);
            }
        }

        return detail;
    }

    private boolean isTerminalStatus(RunStatus status) {
        return status == RunStatus.SUCCESS || status == RunStatus.FAILED
                || status == RunStatus.PARTIAL || status == RunStatus.TIMEOUT;
    }

    private AiRecommendationRunDetailDTO toRunDetailDto(
            AiRecommendationRun run,
            List<AiRecommendationItem> items,
            List<AiRagCitation> citations) {
        return AiRecommendationRunDetailDTO.builder()
                .run(runMapper.toDto(run))
                .diagnostic(diagnosticResultRepository.findByRunId(run.getId())
                        .map(this::toDiagnosticDto)
                        .orElse(null))
                .items(items == null ? Collections.emptyList() : items.stream().map(this::toItemDto).toList())
                .citations(citations == null ? Collections.emptyList() : citations.stream().map(this::toCitationDto).toList())
                .build();
    }

    private AiRecommendationRunDetailDTO.DiagnosticDTO toDiagnosticDto(RuleBasedDiagnosticResult diagnostic) {
        return AiRecommendationRunDetailDTO.DiagnosticDTO.builder()
                .id(diagnostic.getId())
                .title(diagnostic.getTitle())
                .itemJson(diagnostic.getItemJson())
                .assessmentJson(diagnostic.getAssessmentJson())
                .explanationJson(diagnostic.getExplanationJson())
                .createdAt(diagnostic.getCreatedAt())
                .updatedAt(diagnostic.getUpdatedAt())
                .build();
    }

    private AiRecommendationRunDetailDTO.ItemDTO toItemDto(AiRecommendationItem item) {
        return AiRecommendationRunDetailDTO.ItemDTO.builder()
                .id(item.getId())
                .category(item.getCategory() != null ? item.getCategory().name() : null)
                .title(item.getTitle())
                .priorityOrder(item.getPriorityOrder())
                .isPrimary(item.getIsPrimary())
                .itemJson(readItemJson(item.getItemJson()))
                .createdBy(item.getCreatedBy())
                .updatedBy(item.getUpdatedBy())
                .build();
    }

    private AiRecommendationRunDetailDTO.CitationDTO toCitationDto(AiRagCitation citation) {
        return AiRecommendationRunDetailDTO.CitationDTO.builder()
                .id(citation.getId())
                .sourceType(citation.getSourceType() != null ? citation.getSourceType().name() : null)
                .sourceTitle(citation.getSourceTitle())
                .sourceUri(citation.getSourceUri())
                .snippet(citation.getSnippet())
                .relevanceScore(citation.getRelevanceScore())
                .citedFor(citation.getCitedFor())
                .createdBy(citation.getCreatedBy())
                .updatedBy(citation.getUpdatedBy())
                .build();
    }

    private Object readItemJson(String itemJson) {
        if (itemJson == null || itemJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(itemJson, Object.class);
        } catch (Exception e) {
            log.warn("Failed to parse itemJson for API response; returning raw JSON string");
            return itemJson;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getRunHistory(Long episodeId, Pageable pageable) {
        recommendationAccessService.assertCanReviewEpisode(episodeId);
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode not found: " + episodeId);
        }

        Page<AiRecommendationRun> page = runRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId, pageable);
        page.getContent().forEach(r -> {
            Hibernate.initialize(r.getEpisode());
            if (r.getEpisode() != null)
                Hibernate.initialize(r.getEpisode().getPatient());
            if (r.getSnapshot() != null)
                Hibernate.initialize(r.getSnapshot());
        });

        PaginationResultDTO.Meta meta = new PaginationResultDTO.Meta();
        meta.setPage(page.getNumber() + 1);
        meta.setPageSize(page.getSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());

        PaginationResultDTO result = new PaginationResultDTO();
        result.setMeta(meta);
        result.setResult(page.getContent());
        return result;
    }

    @Override
    public AiRecommendationRunDetailDTO retryRun(Long runId) {
        recommendationAccessService.assertCanAccessRun(runId);
        AiRecommendationRun existingRun = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));

        if (existingRun.getStatus() != RunStatus.FAILED && existingRun.getStatus() != RunStatus.TIMEOUT) {
            throw new IllegalStateException("Can only retry FAILED or TIMEOUT runs");
        }

        return generateRecommendationAsync(
                existingRun.getEpisode().getId(),
                existingRun.getTriggerType(),
                existingRun.getRecommendationScope());
    }

    // Long enough to outlast any plausible run; the row is durable so the
    // worker's redis check is just a fast-path. Workers also re-check at the
    // end and drop the result if the row is CANCELLED.
    private static final long CANCEL_KEY_TTL_SECONDS = 1800L; // 30 minutes

    @Override
    @Transactional
    public void cancelRun(Long runId) {
        AiRecommendationRun current = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthenticated");
        }
        if (current.getCreatedByUserId() != null && !current.getCreatedByUserId().equals(currentUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only cancel runs you started");
        }
        if (current.getStatus() != RunStatus.QUEUED
                && current.getStatus() != RunStatus.PROCESSING
                && current.getStatus() != RunStatus.CANCELLED) {
            throw new BusinessException("Run is not cancellable (current status: " + current.getStatus() + ")");
        }

        // Set the fast-path Redis signal before taking the DB lock so an
        // in-flight Python worker or result consumer can observe cancellation
        // even while this transaction waits on the run row.
        redisService.markRunCancelled(runId, CANCEL_KEY_TTL_SECONDS);

        AiRecommendationRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));

        if (run.getCreatedByUserId() != null && !run.getCreatedByUserId().equals(currentUserId)) {
            redisService.clearRunCancelled(runId);
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only cancel runs you started");
        }

        RunStatus status = run.getStatus();
        if (status == RunStatus.CANCELLED) {
            redisService.evictRunDetail(runId);
            log.info("RunId={} was already cancelled; treating cancel as idempotent", runId);
            return;
        }
        if (status != RunStatus.QUEUED && status != RunStatus.PROCESSING) {
            redisService.clearRunCancelled(runId);
            throw new BusinessException("Run is not cancellable (current status: " + status + ")");
        }

        run.setStatus(RunStatus.CANCELLED);
        run.setErrorMessage("Cancelled by user");
        runRepository.save(run);
        redisService.evictRunDetail(runId);

        log.info("Cancelled runId={} by userId={}", runId, currentUserId);
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
}
