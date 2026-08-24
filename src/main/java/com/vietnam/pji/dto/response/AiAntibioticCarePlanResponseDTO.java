package com.vietnam.pji.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** Wire response returned by the dedicated FastAPI care-plan endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAntibioticCarePlanResponseDTO implements Serializable {

    @JsonProperty("request_id")
    private String requestId;

    private String status;
    private ModelInfo model;

    @JsonProperty("latency_ms")
    private Long latencyMs;

    @JsonProperty("care_plan")
    private Map<String, Object> carePlan;

    private List<Citation> citations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo implements Serializable {
        private String name;
        private String version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation implements Serializable {
        @JsonProperty("source_title")
        private String sourceTitle;

        @JsonProperty("source_uri")
        private String sourceUri;

        private String snippet;
    }
}
