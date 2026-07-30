package com.vietnam.pji.services.diagnosis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PjiDiagnosticRuleEngineTest {

    private final PjiDiagnosticRuleEngine engine = new PjiDiagnosticRuleEngine();

    @Test
    void evaluateUsesMajorCultureCriteriaAndFlatLabTemplateShape() {
        PjiDiagnosticRuleEngine.DiagnosticResult result = engine.evaluate(Map.of(
                "clinical_records", Map.of(
                        "symptoms", Map.of("sinus_tract", false),
                        "infection_assessment", Map.of("prosthesis_joint", "HIP_RIGHT")),
                "lab_results", Map.of(
                        "esr", Map.of("value", 18, "unit", "mm/h"),
                        "crp", Map.of("value", 95.3, "unit", "mg/L"),
                        "d_dimer", Map.of("value", 1.85, "unit", "mg/L FEU"),
                        "synovial_wbc", Map.of("value", 52000, "unit", "cells/µL"),
                        "synovial_pmn", Map.of("value", 92, "unit", "%"),
                        "alpha_defensin", Map.of("value", "NOT DETECTED")),
                "culture_results", Map.of("items", List.of(
                        Map.of("organism_name", "Staphylococcus aureus", "result_status", "POSITIVE"),
                        Map.of("organism_name", "Staphylococcus aureus", "result_status", "POSITIVE")))));

        Map<String, Object> itemJson = result.itemJson();
        Map<String, Object> scoringSystem = map(itemJson.get("scoring_system"));
        Map<String, Object> majorCriteria = map(itemJson.get("major_criteria"));
        Map<String, Object> reasoning = map(itemJson.get("ai_reasoning"));
        Map<String, Object> identifiedOrganism = map(reasoning.get("identified_organism"));

        assertEquals("INFECTED", scoringSystem.get("interpretation"));
        assertEquals(7, scoringSystem.get("total_score"));
        assertTrue((Boolean) majorCriteria.get("major_criteria_met"));
        assertEquals("Staphylococcus aureus", identifiedOrganism.get("name"));

        Map<String, Object> minorCriteria = map(itemJson.get("minor_criteria_scoring"));
        List<Map<String, Object>> minorItems = listOfMaps(minorCriteria.get("items"));
        Map<String, Object> alphaDefensin = minorItems.stream()
                .filter(item -> "Positive Alpha-Defensin".equals(item.get("criterion")))
                .findFirst()
                .orElseThrow();

        assertFalse((Boolean) alphaDefensin.get("result"));
        assertEquals(0, alphaDefensin.get("score_awarded"));
    }

    @Test
    void evaluateUsesOnsetTimingAndSuspectedTransmissionRoute() {
        PjiDiagnosticRuleEngine.DiagnosticResult result = engine.evaluate(Map.of(
                "clinical_records", Map.of(
                        "symptoms", Map.of("sinus_tract", false),
                        "infection_assessment", Map.of(
                                "onset_timing", "DELAYED_SUBACUTE",
                                "suspected_transmission_route", "CONTIGUOUS_SPREAD"))));

        Map<String, Object> reasoning = map(result.itemJson().get("ai_reasoning"));

        assertEquals("DELAYED_SUBACUTE", reasoning.get("infection_classification"));
        assertTrue(reasoning.get("infection_classification_reasoning").toString()
                .contains("CONTIGUOUS_SPREAD"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
