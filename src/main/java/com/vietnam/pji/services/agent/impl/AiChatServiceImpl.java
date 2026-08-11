package com.vietnam.pji.services.agent.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.ChatType;
import com.vietnam.pji.dto.request.AiChatRequestDTO;
import com.vietnam.pji.dto.request.CreateChatSessionRequestDTO;
import com.vietnam.pji.dto.request.SendChatMessageRequestDTO;
import com.vietnam.pji.dto.response.AiChatResponseDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.model.agentic.*;
import com.vietnam.pji.repository.*;
import com.vietnam.pji.repository.ai.AiChatMessageRepository;
import com.vietnam.pji.repository.ai.AiChatSessionRepository;
import com.vietnam.pji.repository.ai.AiRecommendationItemRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.agent.AiChatService;
import com.vietnam.pji.services.agent.AiServiceClient;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatServiceImpl implements AiChatService {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final EpisodeRepository episodeRepository;
    private final AiRecommendationRunRepository runRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final AiServiceClient aiServiceClient;
    private final EpisodeSnapshotAssemblerService snapshotAssemblerService;
    private final ObjectMapper objectMapper;
    private final RecommendationAccessService recommendationAccessService;

    @Override
    @Transactional
    public AiChatSession createSession(CreateChatSessionRequestDTO request) {
        if (request.getEpisodeId() != null) {
            recommendationAccessService.assertCanAccessEpisode(request.getEpisodeId());
        }
        if (request.getRunId() != null) {
            recommendationAccessService.assertCanAccessRun(request.getRunId());
        }

        AiChatSession session = AiChatSession.builder()
                .chatType(parseChatType(request.getChatType()))
                .title(request.getTitle())
                .build();

        if (request.getEpisodeId() != null) {
            session.setEpisode(episodeRepository.findById(request.getEpisodeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Episode not found")));
        }

        if (request.getRunId() != null) {
            session.setRun(runRepository.findById(request.getRunId())
                    .orElseThrow(() -> new ResourceNotFoundException("Run not found")));
        }

        if (request.getCurrentItemId() != null) {
            AiRecommendationItem item = itemRepository.findById(request.getCurrentItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
            if (item.getRun() == null) {
                throw new ForbiddenException("Recommendation item is not linked to a run");
            }
            recommendationAccessService.assertCanAccessRun(item.getRun().getId());
            session.setCurrentItem(item);
        }

        if (session.getEpisode() != null && session.getRun() != null
                && (session.getRun().getEpisode() == null
                || !session.getEpisode().getId().equals(session.getRun().getEpisode().getId()))) {
            throw new ForbiddenException("AI recommendation run does not belong to this episode");
        }
        if (session.getRun() != null && session.getCurrentItem() != null
                && !session.getRun().getId().equals(session.getCurrentItem().getRun().getId())) {
            throw new ForbiddenException("Recommendation item does not belong to this run");
        }

        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public AiChatMessage sendMessage(Long sessionId, SendChatMessageRequestDTO request) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));
        assertCanAccessSession(session);

        // Save user message
        AiChatMessage userMessage = AiChatMessage.builder()
                .session(session)
                .role("user")
                .content(request.getContent())
                .build();
        messageRepository.save(userMessage);

        // Build AI request
        AiChatRequestDTO aiRequest = buildChatRequest(session, request);

        // Call AI service
        AiChatResponseDTO aiResponse;
        try {
            aiResponse = aiServiceClient.chat(aiRequest);
        } catch (Exception e) {
            log.error("AI chat service call failed for sessionId={}", sessionId, e);
            throw new RuntimeException("AI chat service call failed: " + e.getMessage(), e);
        }

        // Save assistant message
        AiChatMessage assistantMessage = AiChatMessage.builder()
                .session(session)
                .role("assistant")
                .content(aiResponse.getAnswer())
                .latencyMs(aiResponse.getLatencyMs())
                .tokensUsed(aiResponse.getTokensUsed())
                .build();

        if (aiResponse.getReferences() != null) {
            try {
                assistantMessage.setReferencesJson(objectMapper.writeValueAsString(aiResponse.getReferences()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize chat references", e);
            }
        }

        return messageRepository.save(assistantMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getMessages(Long sessionId, Pageable pageable) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));
        assertCanAccessSession(session);

        Page<AiChatMessage> page = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId, pageable);
        page.getContent().forEach(m -> Hibernate.initialize(m.getSession()));

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
    @Transactional(readOnly = true)
    public PaginationResultDTO getSessionsByEpisode(Long episodeId, Pageable pageable) {
        recommendationAccessService.assertCanAccessEpisode(episodeId);
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode not found: " + episodeId);
        }

        Page<AiChatSession> page = sessionRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId, pageable);
        page.getContent().forEach(s -> {
            Hibernate.initialize(s.getEpisode());
            if (s.getRun() != null)
                Hibernate.initialize(s.getRun());
            if (s.getCurrentItem() != null)
                Hibernate.initialize(s.getCurrentItem());
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

    private AiChatRequestDTO buildChatRequest(AiChatSession session, SendChatMessageRequestDTO request) {
        AiChatRequestDTO.AiChatRequestDTOBuilder builder = AiChatRequestDTO.builder()
                .question(request.getContent());

        // Episode context
        if (request.isUseEpisodeContext() && session.getEpisode() != null) {
            try {
                var snapshotResult = snapshotAssemblerService.buildSnapshot(session.getEpisode().getId());
                builder.episodeSummary(snapshotResult.getSnapshotDataJson());
            } catch (Exception e) {
                log.warn("Failed to build episode context for chat, sessionId={}", session.getId(), e);
            }
        }

        // Run/item context
        if (request.isUseRunContext() && session.getRun() != null) {
            Map<String, Object> recContext = new LinkedHashMap<>();
            // recContext.put("assessment", session.getRun().getAssessmentJson());
            if (session.getCurrentItem() != null) {
                recContext.put("current_item", session.getCurrentItem().getItemJson());
            }
            // Include all items for the run
            List<AiRecommendationItem> items = itemRepository
                    .findByRunIdOrderByPriorityOrderAsc(session.getRun().getId());
            List<Map<String, String>> itemSummaries = items.stream()
                    .map(i -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("category", i.getCategory().name());
                        m.put("title", i.getTitle());
                        return m;
                    })
                    .collect(Collectors.toList());
            recContext.put("items", itemSummaries);
            builder.recommendationContext(recContext);
        }

        // Chat history (last 20 messages)
        if (request.isUseChatHistory()) {
            List<AiChatMessage> recentMessages = messageRepository
                    .findTop20BySessionIdOrderByCreatedAtDesc(session.getId());
            Collections.reverse(recentMessages); // oldest first
            List<AiChatRequestDTO.ChatMessageDTO> history = recentMessages.stream()
                    .map(m -> AiChatRequestDTO.ChatMessageDTO.builder()
                            .role(m.getRole())
                            .content(m.getContent())
                            .build())
                    .collect(Collectors.toList());
            builder.chatHistory(history);
        }

        return builder.build();
    }

    private void assertCanAccessSession(AiChatSession session) {
        if (session.getRun() != null) {
            recommendationAccessService.assertCanAccessRun(session.getRun().getId());
            return;
        }
        if (session.getEpisode() != null) {
            recommendationAccessService.assertCanAccessEpisode(session.getEpisode().getId());
            return;
        }
        throw new ForbiddenException("AI chat session is not linked to a medical record");
    }

    private ChatType parseChatType(String chatType) {
        try {
            return ChatType.valueOf(chatType);
        } catch (Exception e) {
            return ChatType.GENERAL;
        }
    }
}
