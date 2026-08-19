package com.vietnam.pji.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationRunDetailDTO implements Serializable {

    private AiRecommendationRunDTO run;
    private DiagnosticDTO diagnostic;
    private List<ItemDTO> items;
    private List<CitationDTO> citations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDTO implements Serializable {
        private Long id;
        private String category;
        private String title;
        private Integer priorityOrder;
        private Boolean isPrimary;
        private Object itemJson;
        private String createdBy;
        private String updatedBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosticDTO implements Serializable {
        private Long id;
        private String title;
        private Map<String, Object> itemJson;
        private Map<String, Object> assessmentJson;
        private Map<String, Object> explanationJson;
        private Date createdAt;
        private Date updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitationDTO implements Serializable {
        private Long id;
        private String sourceType;
        private String sourceTitle;
        private String sourceUri;
        private String snippet;
        private BigDecimal relevanceScore;
        private String citedFor;
        private String createdBy;
        private String updatedBy;
    }
}
