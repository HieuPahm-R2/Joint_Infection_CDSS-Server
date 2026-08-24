package com.vietnam.pji.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.ItemCategory;
import com.vietnam.pji.constant.RecommendationScope;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRunPersistenceContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recommendationItemEnumContainsOnlyTreatmentCategories() {
        assertThat(Arrays.stream(ItemCategory.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "SYSTEMIC_ANTIBIOTIC",
                        "SURGERY_PROCEDURE",
                        "LOCAL_ANTIBIOTIC",
                        "ANTIBIOTIC_CARE_PLAN");
    }

    @Test
    void recommendationScopeOwnsItsExactOutputContract() {
        assertThat(RecommendationScope.SURGERY.requiredItemCategories())
                .containsExactly(ItemCategory.SURGERY_PROCEDURE);
        assertThat(RecommendationScope.ANTIBIOTIC.requiredItemCategories())
                .containsExactlyInAnyOrder(
                        ItemCategory.SYSTEMIC_ANTIBIOTIC,
                        ItemCategory.LOCAL_ANTIBIOTIC,
                        ItemCategory.ANTIBIOTIC_CARE_PLAN);
    }

    @Test
    void runDetailSerializesDiagnosisSeparatelyFromTreatmentItems() throws Exception {
        AiRecommendationRunDTO run = AiRecommendationRunDTO.builder()
                .id(41L)
                .build();
        AiRecommendationRunDetailDTO detail = AiRecommendationRunDetailDTO.builder()
                .run(run)
                .diagnostic(AiRecommendationRunDetailDTO.DiagnosticDTO.builder()
                        .id(51L)
                        .title("Rule diagnosis")
                        .itemJson(Map.of("diagnostic_method", "RULE_BASED_BACKEND"))
                        .assessmentJson(Map.of("pji_probability", "INFECTED"))
                        .explanationJson(Map.of("clinical_reasoning", "Major criterion met"))
                        .build())
                .items(List.of(AiRecommendationRunDetailDTO.ItemDTO.builder()
                        .id(61L)
                        .category("SURGERY_PROCEDURE")
                        .build()))
                .citations(List.of())
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(detail));

        assertThat(json.path("run").has("assessmentJson")).isFalse();
        assertThat(json.path("run").has("explanationJson")).isFalse();
        assertThat(json.path("run").has("warningsJson")).isFalse();
        assertThat(json.path("diagnostic").path("assessmentJson").path("pji_probability").asText())
                .isEqualTo("INFECTED");
        assertThat(json.path("diagnostic").has("warningsJson")).isFalse();
        assertThat(json.path("items").get(0).path("category").asText())
                .isEqualTo("SURGERY_PROCEDURE");
    }
}
