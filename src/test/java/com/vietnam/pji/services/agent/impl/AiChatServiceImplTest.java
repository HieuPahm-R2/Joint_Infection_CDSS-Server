package com.vietnam.pji.services.agent.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.vietnam.pji.dto.request.AiChatRequestDTO;
import com.vietnam.pji.dto.request.SendChatMessageRequestDTO;
import com.vietnam.pji.dto.response.AiChatResponseDTO;
import com.vietnam.pji.model.agentic.AiChatMessage;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiChatMessageRepository;
import com.vietnam.pji.repository.ai.AiChatSessionRepository;
import com.vietnam.pji.repository.ai.AiRecommendationItemRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.agent.AiServiceClient;
import com.vietnam.pji.services.agent.RecommendationAccessService;

class AiChatServiceImplTest {

    private final AiChatSessionRepository sessionRepository = mock(AiChatSessionRepository.class);
    private final AiChatMessageRepository messageRepository = mock(AiChatMessageRepository.class);
    private final EpisodeRepository episodeRepository = mock(EpisodeRepository.class);
    private final AiRecommendationRunRepository runRepository = mock(AiRecommendationRunRepository.class);
    private final AiRecommendationItemRepository itemRepository = mock(AiRecommendationItemRepository.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final RecommendationAccessService accessService = mock(RecommendationAccessService.class);
    private final AiChatTurnPreparer preparer = mock(AiChatTurnPreparer.class);
    private final AiChatTurnWriter writer = mock(AiChatTurnWriter.class);
    private final AiChatServiceImpl service = new AiChatServiceImpl(
            sessionRepository,
            messageRepository,
            episodeRepository,
            runRepository,
            itemRepository,
            aiServiceClient,
            accessService,
            preparer,
            writer);

    @Test
    void persistsExchangeOnlyAfterAiResponds() {
        SendChatMessageRequestDTO request = new SendChatMessageRequestDTO();
        request.setContent("What is the recommendation?");
        AiChatRequestDTO aiRequest = AiChatRequestDTO.builder().question(request.getContent()).build();
        AiChatResponseDTO response = mock(AiChatResponseDTO.class);
        AiChatMessage expected = AiChatMessage.builder().role("assistant").content("Answer").build();
        when(preparer.prepare(5L, request))
                .thenReturn(new AiChatTurnPreparer.PreparedChatTurn(5L, aiRequest));
        when(aiServiceClient.chat(aiRequest)).thenReturn(response);
        when(writer.write(5L, request, response)).thenReturn(expected);

        AiChatMessage actual = service.sendMessage(5L, request);

        assertThat(actual).isSameAs(expected);
        verify(writer).write(5L, request, response);
    }

    @Test
    void doesNotPersistExchangeWhenAiCallFails() {
        SendChatMessageRequestDTO request = new SendChatMessageRequestDTO();
        request.setContent("What is the recommendation?");
        AiChatRequestDTO aiRequest = AiChatRequestDTO.builder().question(request.getContent()).build();
        when(preparer.prepare(5L, request))
                .thenReturn(new AiChatTurnPreparer.PreparedChatTurn(5L, aiRequest));
        when(aiServiceClient.chat(aiRequest)).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> service.sendMessage(5L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI chat service call failed");

        verify(writer, never()).write(eq(5L), any(), any());
    }
}
