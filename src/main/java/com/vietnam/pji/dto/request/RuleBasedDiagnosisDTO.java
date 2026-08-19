package com.vietnam.pji.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** Stable cross-service representation of the backend rule-engine result. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleBasedDiagnosisDTO implements Serializable {

    private String title;

    @JsonProperty("item_json")
    private Map<String, Object> itemJson;

    @JsonProperty("assessment_json")
    private Map<String, Object> assessmentJson;

    @JsonProperty("explanation_json")
    private Map<String, Object> explanationJson;

    public static RuleBasedDiagnosisDTO from(PjiDiagnosticRuleEngine.DiagnosticResult result) {
        return RuleBasedDiagnosisDTO.builder()
                .title(result.title())
                .itemJson(result.itemJson())
                .assessmentJson(result.assessmentJson())
                .explanationJson(result.explanationJson())
                .build();
    }
}
