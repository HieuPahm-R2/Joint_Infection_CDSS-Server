package com.vietnam.pji.services.agent;

import com.vietnam.pji.dto.request.AiChatRequestDTO;
import com.vietnam.pji.dto.request.AiRecommendationGenerateRequestDTO;
import com.vietnam.pji.dto.response.AiChatResponseDTO;
import com.vietnam.pji.dto.response.AiRecommendationGenerateResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class AiServiceClient {

    private final RestTemplate aiRestTemplate;

    public AiServiceClient(@Qualifier("aiRestTemplate") RestTemplate aiRestTemplate) {
        this.aiRestTemplate = aiRestTemplate;
    }

    public AiRecommendationGenerateResponseDTO generateRecommendation(AiRecommendationGenerateRequestDTO request) {
        log.info("Calling AI service for recommendation generation, requestId={}", request.getRequestId());

        return aiRestTemplate.postForObject(
                "/api/v1/process-snapshot",
                request,
                AiRecommendationGenerateResponseDTO.class);
    }

    public AiChatResponseDTO chat(AiChatRequestDTO request) {
        log.info("Calling AI service for chat");

        return aiRestTemplate.postForObject(
                "/api/v1/chat",
                request,
                AiChatResponseDTO.class);
    }
}
