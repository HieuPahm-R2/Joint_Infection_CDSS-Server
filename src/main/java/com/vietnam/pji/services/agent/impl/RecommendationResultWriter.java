package com.vietnam.pji.services.agent.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.ItemCategory;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.constant.SourceType;
import com.vietnam.pji.dto.response.AiRecommendationGenerateResponseDTO;
import com.vietnam.pji.dto.response.AiRecommendationRunDetailDTO;
import com.vietnam.pji.exception.BusinessException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiRagCitation;
import com.vietnam.pji.model.agentic.AiRecommendationItem;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.RuleBasedDiagnosticResult;
import com.vietnam.pji.repository.ai.AiRagCitationRepository;
import com.vietnam.pji.repository.ai.AiRecommendationItemRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.repository.ai.RuleBasedDiagnosticResultRepository;
import com.vietnam.pji.utils.mapper.AiRecommendationRunMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists the AI service's synchronous recommendation response.
 *
 * <p>
 * This is a separate bean on purpose. It used to be a
 * {@code @Transactional protected} method on {@code AiRecommendationServiceImpl}
 * called through {@code this.saveAiResults(...)} from a non-transactional
 * caller, so the annotation never applied and the run row, its items and its
 * citations were written non-atomically: a failure mid-loop left a
 * {@code SUCCESS}/{@code PARTIAL} run with a partial item set. Crossing the bean
 * boundary makes the {@code @Transactional} on {@link #save} effective.
 *
 * <p>
 * The result mapping ({@code getRunDetail}-shaped DTO) lives here too, so this
 * class is the single owner of "what a persisted recommendation run looks like".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationResultWriter {

    private final AiRecommendationRunRepository runRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final AiRagCitationRepository citationRepository;
    private final RuleBasedDiagnosticResultRepository diagnosticResultRepository;
    private final ObjectMapper objectMapper;
    private final AiRecommendationRunMapper runMapper;

    /**
     * Validates the AI payload, updates the run, and persists its items and
     * citations in one transaction.
     *
     * @param runId    run created before the AI call
     * @param response AI service response
     * @return the run detail DTO, the same shape {@code getRunDetail} returns
     * @throws ResourceNotFoundException when the run disappeared
     * @throws BusinessException         when the item categories do not match the
     *                                   run's recommendation scope
     */
    @Transactional
    public AiRecommendationRunDetailDTO save(Long runId, AiRecommendationGenerateResponseDTO response) {
        AiRecommendationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));

        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage("AI response missing required items");
            runRepository.save(run);
            throw new BusinessException("AI response validation failed: missing items");
        }

        RecommendationScope scope = run.getRecommendationScope();
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

        run.setStatus("SUCCESS".equals(response.getStatus()) ? RunStatus.SUCCESS : RunStatus.PARTIAL);
        if (response.getModel() != null) {
            run.setModelName(response.getModel().getName());
            run.setModelVersion(response.getModel().getVersion());
        }
        run.setLatencyMs(response.getLatencyMs());
        runRepository.save(run);

        Map<String, AiRecommendationItem> itemKeyMap = saveItems(run, response.getItems());
        saveCitations(run, response.getCitations(), itemKeyMap);

        return toRunDetailDto(
                run,
                itemRepository.findByRunIdOrderByPriorityOrderAsc(runId),
                citationRepository.findByRunId(runId));
    }

    private Map<String, AiRecommendationItem> saveItems(
            AiRecommendationRun run, List<AiRecommendationGenerateResponseDTO.ItemDTO> items) {
        Map<String, AiRecommendationItem> itemKeyMap = new HashMap<>();
        for (AiRecommendationGenerateResponseDTO.ItemDTO itemDTO : items) {
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
        return itemKeyMap;
    }

    /**
     * Maps citations to their item through {@code client_item_key}; a citation
     * naming an item that was never saved is dropped rather than stored orphaned.
     */
    private void saveCitations(AiRecommendationRun run,
            List<AiRecommendationGenerateResponseDTO.CitationDTO> citations,
            Map<String, AiRecommendationItem> itemKeyMap) {
        if (citations == null) {
            return;
        }
        for (AiRecommendationGenerateResponseDTO.CitationDTO citationDTO : citations) {
            if (citationDTO.getClientItemKey() != null && !itemKeyMap.containsKey(citationDTO.getClientItemKey())) {
                log.debug("Skipping citation for unmapped AI item key={} on runId={}",
                        citationDTO.getClientItemKey(), run.getId());
                continue;
            }
            AiRagCitation citation = AiRagCitation.builder()
                    .run(run)
                    .sourceType(parseSourceType(citationDTO.getSourceType()))
                    .sourceTitle(citationDTO.getSourceTitle())
                    .sourceUri(citationDTO.getSourceUri())
                    .snippet(citationDTO.getSnippet())
                    .relevanceScore(citationDTO.getRelevanceScore())
                    .citedFor(citationDTO.getCitedFor())
                    .build();
            if (citationDTO.getClientItemKey() != null) {
                citation.setItem(itemKeyMap.get(citationDTO.getClientItemKey()));
            }
            citationRepository.save(citation);
        }
    }

    AiRecommendationRunDetailDTO toRunDetailDto(
            AiRecommendationRun run,
            List<AiRecommendationItem> items,
            List<AiRagCitation> citations) {
        return AiRecommendationRunDetailDTO.builder()
                .run(runMapper.toDto(run))
                .diagnostic(diagnosticResultRepository.findByRunId(run.getId())
                        .map(this::toDiagnosticDto)
                        .orElse(null))
                .items(items == null ? Collections.emptyList() : items.stream().map(this::toItemDto).toList())
                .citations(citations == null ? Collections.emptyList()
                        : citations.stream().map(this::toCitationDto).toList())
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
