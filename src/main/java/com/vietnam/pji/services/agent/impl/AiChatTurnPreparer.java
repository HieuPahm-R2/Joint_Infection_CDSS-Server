package com.vietnam.pji.services.agent.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietnam.pji.dto.request.AiChatRequestDTO;
import com.vietnam.pji.dto.request.SendChatMessageRequestDTO;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiChatMessage;
import com.vietnam.pji.model.agentic.AiChatSession;
import com.vietnam.pji.model.agentic.AiRecommendationItem;
import com.vietnam.pji.repository.ai.AiChatMessageRepository;
import com.vietnam.pji.repository.ai.AiChatSessionRepository;
import com.vietnam.pji.repository.ai.AiRecommendationItemRepository;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Reads and authorizes a chat turn without retaining a transaction during AI I/O. */
@Service
@RequiredArgsConstructor
@Slf4j
class AiChatTurnPreparer {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final EpisodeSnapshotAssemblerService snapshotAssemblerService;
    private final RecommendationAccessService recommendationAccessService;

    @Transactional(readOnly = true)
    PreparedChatTurn prepare(Long sessionId, SendChatMessageRequestDTO request) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));
        assertCanAccessSession(session);
        return new PreparedChatTurn(session.getId(), buildChatRequest(session, request));
    }

    private AiChatRequestDTO buildChatRequest(AiChatSession session, SendChatMessageRequestDTO request) {
        AiChatRequestDTO.AiChatRequestDTOBuilder builder = AiChatRequestDTO.builder()
                .question(request.getContent());

        if (request.isUseEpisodeContext() && session.getEpisode() != null) {
            try {
                var snapshotResult = snapshotAssemblerService.buildSnapshot(session.getEpisode().getId());
                builder.episodeSummary(snapshotResult.getSnapshotDataJson());
            } catch (Exception exception) {
                log.warn("Failed to build episode context for chat, sessionId={}", session.getId(), exception);
            }
        }

        if (request.isUseRunContext() && session.getRun() != null) {
            Map<String, Object> recommendationContext = new LinkedHashMap<>();
            if (session.getCurrentItem() != null) {
                recommendationContext.put("current_item", session.getCurrentItem().getItemJson());
            }
            List<AiRecommendationItem> items = itemRepository
                    .findByRunIdOrderByPriorityOrderAsc(session.getRun().getId());
            recommendationContext.put("items", items.stream()
                    .map(item -> {
                        Map<String, String> summary = new LinkedHashMap<>();
                        summary.put("category", item.getCategory().name());
                        summary.put("title", item.getTitle());
                        return summary;
                    })
                    .collect(Collectors.toList()));
            builder.recommendationContext(recommendationContext);
        }

        if (request.isUseChatHistory()) {
            List<AiChatMessage> recentMessages = new ArrayList<>(messageRepository
                    .findTop20BySessionIdOrderByCreatedAtDesc(session.getId()));
            Collections.reverse(recentMessages);
            List<AiChatRequestDTO.ChatMessageDTO> history = recentMessages.stream()
                    .map(message -> AiChatRequestDTO.ChatMessageDTO.builder()
                            .role(message.getRole())
                            .content(message.getContent())
                            .build())
                    .collect(Collectors.toCollection(ArrayList::new));
            // The user message has not been persisted yet, so add it explicitly
            // to preserve the request sent by the previous transactional flow.
            history.add(AiChatRequestDTO.ChatMessageDTO.builder()
                    .role("user")
                    .content(request.getContent())
                    .build());
            builder.chatHistory(history);
        }

        return builder.build();
    }

    void assertCanAccessSession(AiChatSession session) {
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

    record PreparedChatTurn(Long sessionId, AiChatRequestDTO request) {
    }
}
