package com.vietnam.pji.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Payload sent from Spring Boot to FastAPI for recommendation generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationGenerateRequestDTO implements Serializable {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("trigger_type")
    private String triggerType;

    @JsonProperty("episode_id")
    private Long episodeId;

    @JsonProperty("snapshot_id")
    private Long snapshotId;

    @JsonProperty("snapshot_data_json")
    private Map<String, Object> snapshotDataJson;

    /** Backend-owned diagnosis used by RAG as the treatment-planning baseline. */
    @JsonProperty("rule_based_diagnosis")
    private RuleBasedDiagnosisDTO ruleBasedDiagnosis;

    private Options options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Options implements Serializable {
        @Builder.Default
        private String language = "vi";

        @JsonProperty("include_citations")
        @Builder.Default
        private boolean includeCitations = true;

        @JsonProperty("top_k")
        @Builder.Default
        private int topK = 5;
    }
}
