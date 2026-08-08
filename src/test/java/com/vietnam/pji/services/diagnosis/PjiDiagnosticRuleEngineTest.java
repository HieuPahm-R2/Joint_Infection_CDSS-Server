package com.vietnam.pji.services.diagnosis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PjiDiagnosticRuleEngineTest {

    private final PjiDiagnosticSnapshotReader snapshotReader = new PjiDiagnosticSnapshotReader();
    private final PjiDiagnosticRuleEngine engine = new PjiDiagnosticRuleEngine(
            new PjiCultureEvidenceEvaluator(snapshotReader),
            new PjiDiagnosticCriteriaEvaluator(snapshotReader),
            new PjiDiagnosticReportBuilder(snapshotReader));

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
        Map<String, Object> synovialGroup = minorItems.stream()
                .filter(item -> item.get("criterion").toString().contains("Alpha-Defensin"))
                .findFirst()
                .orElseThrow();

        assertTrue((Boolean) synovialGroup.get("result"));
        assertEquals(3, synovialGroup.get("score_awarded"));
        assertTrue(synovialGroup.get("result_detail").toString().contains("không đạt cutoff 1.0"));
        assertEquals(7, minorItems.size());
        assertFalse(minorItems.stream().anyMatch(item -> item.get("criterion").toString().contains("Synovial CRP")));
    }

    @Test
    void evaluateAppliesAcuteAndChronicThresholdsFromIcmReference() {
        Map<String, Object> labs = Map.of(
                "crp", Map.of("value", 50, "unit", "mg/L"),
                "esr", Map.of("value", 50, "unit", "mm/h"),
                "d_dimer", Map.of("value", 1, "unit", "mg/L FEU"),
                "synovial_wbc", Map.of("value", 5000, "unit", "cells/µL"),
                "synovial_pmn", Map.of("value", 80, "unit", "%"),
                "alpha_defensin", Map.of("value", 0.5));

        PjiDiagnosticRuleEngine.DiagnosticResult acute = engine.evaluate(Map.of(
                "clinical_records", Map.of("infection_assessment", Map.of("onset_timing", "EARLY")),
                "lab_results", labs));
        PjiDiagnosticRuleEngine.DiagnosticResult chronic = engine.evaluate(Map.of("lab_results", labs));

        assertEquals(0, map(acute.itemJson().get("scoring_system")).get("total_score"));
        assertEquals(8, map(chronic.itemJson().get("scoring_system")).get("total_score"));
    }

    @Test
    void evaluateCapsReferenceTableMinorCriteriaAtSixteenPoints() {
        PjiDiagnosticRuleEngine.DiagnosticResult result = engine.evaluate(Map.of(
                "clinical_records", Map.of("symptoms", Map.of("sinus_tract", false)),
                "lab_results", Map.of(
                        "crp", Map.of("value", 11, "unit", "mg/L"),
                        "esr", Map.of("value", 31, "unit", "mm/h"),
                        "synovial_wbc", Map.of("value", 3001, "unit", "cells/µL"),
                        "synovial_pmn", Map.of("value", 71, "unit", "%"),
                        "alpha_defensin", Map.of("value", 1.0)),
                "culture_results", Map.of("items", List.of(
                        Map.of("organism_name", "Staphylococcus epidermidis", "result_status", "POSITIVE"))),
                "surgeries", Map.of("items", List.of(Map.of(
                        "findings", "Positive histology >5 PMN/HPF; purulence present")))));

        Map<String, Object> minor = map(result.itemJson().get("minor_criteria_scoring"));
        List<Map<String, Object>> items = listOfMaps(minor.get("items"));

        assertEquals(16, minor.get("total_minor_score"));
        assertEquals(16, items.stream().mapToInt(item -> (Integer) item.get("score_weight")).sum());
        assertEquals(7, items.size());
    }

    @Test
    void evaluatePrefersStructuredSurgeryEvidenceOverLegacyFindingsText() {
        PjiDiagnosticRuleEngine.DiagnosticResult result = engine.evaluate(Map.of(
                "surgeries", Map.of("items", List.of(Map.of(
                        "positive_histology", false,
                        "intraoperative_purulence", true,
                        "findings", "Positive histology >5 PMN/HPF; no purulence")))));

        Map<String, Object> minor = map(result.itemJson().get("minor_criteria_scoring"));
        List<Map<String, Object>> items = listOfMaps(minor.get("items"));
        Map<String, Object> histology = items.stream()
                .filter(item -> item.get("criterion").toString().contains("Giải phẫu bệnh"))
                .findFirst().orElseThrow();
        Map<String, Object> purulence = items.stream()
                .filter(item -> item.get("criterion").toString().contains("Mủ trong khớp"))
                .findFirst().orElseThrow();

        assertFalse((Boolean) histology.get("result"));
        assertEquals(0, histology.get("score_awarded"));
        assertTrue((Boolean) purulence.get("result"));
        assertEquals(3, purulence.get("score_awarded"));
        assertEquals(3, minor.get("total_minor_score"));
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

    @Test
    void evaluateRetainsCultureResistanceAndDataQualityWarnings() {
        PjiDiagnosticRuleEngine.DiagnosticResult result = engine.evaluate(Map.of(
                "culture_results", Map.of("items", List.of(
                        Map.of(
                                "organism_name", "Staphylococcus aureus",
                                "result_status", "POSITIVE",
                                "had_antibiotics_before", true,
                                "sensitivities", List.of(Map.of(
                                        "antibiotic_name", "Oxacillin",
                                        "sensitivity_code", "R"))),
                        Map.of(
                                "organism_name", "Staphylococcus aureus",
                                "result_status", "POSITIVE")))));

        Map<String, Object> reasoning = map(result.itemJson().get("ai_reasoning"));
        Map<String, Object> identifiedOrganism = map(reasoning.get("identified_organism"));

        assertEquals("MRSA", identifiedOrganism.get("resistance_profile"));
        assertTrue(result.warningsJson().stream()
                .anyMatch(warning -> "DATA_QUALITY".equals(warning.get("type"))));
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
