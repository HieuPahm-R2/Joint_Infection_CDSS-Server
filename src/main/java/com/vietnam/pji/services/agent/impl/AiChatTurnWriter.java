package com.vietnam.pji.services.agent.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.dto.request.SendChatMessageRequestDTO;
import com.vietnam.pji.dto.response.AiChatResponseDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiChatMessage;
import com.vietnam.pji.model.agentic.AiChatSession;
import com.vietnam.pji.repository.ai.AiChatMessageRepository;
import com.vietnam.pji.repository.ai.AiChatSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Persists a successful user/assistant exchange after external AI I/O completes. */
@Service
@RequiredArgsConstructor
@Slf4j
class AiChatTurnWriter {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    AiChatMessage write(Long sessionId, SendChatMessageRequestDTO request, AiChatResponseDTO response) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));
        messageRepository.save(AiChatMessage.builder()
                .session(session)
                .role("user")
                .content(request.getContent())
                .build());

        AiChatMessage assistantMessage = AiChatMessage.builder()
                .session(session)
                .role("assistant")
                .content(response.getAnswer())
                .latencyMs(response.getLatencyMs())
                .tokensUsed(response.getTokensUsed())
                .build();
        if (response.getReferences() != null) {
            try {
                assistantMessage.setReferencesJson(objectMapper.writeValueAsString(response.getReferences()));
            } catch (JsonProcessingException exception) {
                log.warn("Failed to serialize chat references", exception);
            }
        }
        return messageRepository.save(assistantMessage);
    }
}
