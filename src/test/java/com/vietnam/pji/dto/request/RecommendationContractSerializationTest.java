package com.vietnam.pji.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void httpContractCarriesRuleDiagnosisAndNoPriorRecommendationHistory() throws Exception {
        RuleBasedDiagnosisDTO diagnosis = diagnosis();
        AiRecommendationGenerateRequestDTO request = AiRecommendationGenerateRequestDTO.builder()
                .requestId("request-1")
                .episodeId(10L)
                .snapshotId(20L)
                .snapshotDataJson(Map.of("episode", Map.of("id", 10)))
                .ruleBasedDiagnosis(diagnosis)
                .options(AiRecommendationGenerateRequestDTO.Options.builder().build())
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(request));

        assertThat(json.has("prior_accepted_diagnoses")).isFalse();
        assertThat(json.path("rule_based_diagnosis").path("assessment_json")
                .path("pji_probability").asText()).isEqualTo("DEFINITE");
    }

    @Test
    void rabbitContractUsesCamelCaseBoundaryAndSameDiagnosisPayload() throws Exception {
        RabbitMQRecommendationMessage message = RabbitMQRecommendationMessage.builder()
                .requestId("request-1")
                .runId(30L)
                .snapshotDataJson(Map.of("episode", Map.of("id", 10)))
                .ruleBasedDiagnosis(diagnosis())
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(message));

        assertThat(json.has("priorAcceptedDiagnoses")).isFalse();
        assertThat(json.path("ruleBasedDiagnosis").path("item_json")
                .path("result").asText()).isEqualTo("DEFINITE");
    }

    private RuleBasedDiagnosisDTO diagnosis() {
        PjiDiagnosticRuleEngine.DiagnosticResult result = new PjiDiagnosticRuleEngine.DiagnosticResult(
                "Rule diagnosis",
                Map.of("result", "DEFINITE"),
                Map.of("pji_probability", "DEFINITE"),
                Map.of("clinical_reasoning", "Major criterion met"),
                List.of());
        return RuleBasedDiagnosisDTO.from(result);
    }
}
