package com.vietnam.pji.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Message consumed from the RabbitMQ result queue.
 * Published by the Python RAG worker after processing a recommendation.
 * <p>
 * Matches the same structure as AiRecommendationGenerateResponseDTO
 * so the result listener can reuse the existing save logic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RabbitMQRecommendationResultMessage implements Serializable {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("run_id")
    private Long runId;

    private String status; // SUCCESS, PARTIAL, FAILED

    private ModelInfo model;

    @JsonProperty("latency_ms")
    private Long latencyMs;

    private List<ItemDTO> items;

    private List<CitationDTO> citations;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("data_completeness")
    private Map<String, Object> dataCompleteness;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo implements Serializable {
        private String name;
        private String version;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDTO implements Serializable {
        @JsonProperty("client_item_key")
        private String clientItemKey;

        private String category;
        private String title;

        @JsonProperty("priority_order")
        private Integer priorityOrder;

        @JsonProperty("is_primary")
        private Boolean isPrimary;

        @JsonProperty("item_json")
        private Map<String, Object> itemJson;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitationDTO implements Serializable {
        @JsonProperty("client_item_key")
        private String clientItemKey;

        @JsonProperty("source_type")
        private String sourceType;

        @JsonProperty("source_title")
        private String sourceTitle;

        @JsonProperty("source_uri")
        private String sourceUri;

        private String snippet;

        @JsonProperty("relevance_score")
        private BigDecimal relevanceScore;

        @JsonProperty("cited_for")
        private String citedFor;
    }
}
