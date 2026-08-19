package com.vietnam.pji.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

/**
 * Payload returned from FastAPI after recommendation generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationGenerateResponseDTO implements Serializable {

    @JsonProperty("request_id")
    private String requestId;

    private String status;

    private ModelInfo model;

    @JsonProperty("latency_ms")
    private Long latencyMs;

    @JsonProperty("ai_recommendation_items")
    private List<ItemDTO> items;

    @JsonProperty("ai_rag_citations")
    private List<CitationDTO> citations;

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
        /**
         * RAG sends "id" as the item key; Spring uses it as client_item_key
         * to link citations back to items.
         */
        @JsonProperty("id")
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
        /**
         * RAG sends "item_id" to reference which item this citation belongs to.
         */
        @JsonProperty("item_id")
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
