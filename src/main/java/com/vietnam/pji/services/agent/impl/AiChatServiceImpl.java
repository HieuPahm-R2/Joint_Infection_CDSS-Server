package com.vietnam.pji.services.agent.impl;

import com.vietnam.pji.constant.ChatType;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RecommendationAccessService recommendationAccessService;
    private final AiChatTurnPreparer chatTurnPreparer;
    private final AiChatTurnWriter chatTurnWriter;

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
    public AiChatMessage sendMessage(Long sessionId, SendChatMessageRequestDTO request) {
        AiChatTurnPreparer.PreparedChatTurn prepared = chatTurnPreparer.prepare(sessionId, request);
        AiChatResponseDTO response;
        try {
            response = aiServiceClient.chat(prepared.request());
        } catch (Exception exception) {
            log.error("AI chat service call failed for sessionId={}", sessionId, exception);
            throw new RuntimeException("AI chat service call failed: " + exception.getMessage(), exception);
        }
        return chatTurnWriter.write(sessionId, request, response);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO getMessages(Long sessionId, Pageable pageable) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));
        chatTurnPreparer.assertCanAccessSession(session);

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

    private ChatType parseChatType(String chatType) {
        try {
            return ChatType.valueOf(chatType);
        } catch (Exception e) {
            return ChatType.GENERAL;
        }
    }
}
