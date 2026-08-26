package com.vietnam.pji.services.diagnosis;

import com.vietnam.pji.dto.request.PjiDiagnosticEvaluationRequestDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts the manual calculator contract into the canonical snapshot shape. */
@Component
class PjiDiagnosticRequestAdapter {

    Map<String, Object> toSnapshot(PjiDiagnosticEvaluationRequestDTO request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("diagnostic_context", mapOfPresent(
                "previous_arthroplasty", request.previousArthroplasty(),
                "days_since_arthroplasty", request.daysSinceArthroplasty()));

        Map<String, Object> clinicalRecords = new LinkedHashMap<>();
        Map<String, Object> symptoms = new LinkedHashMap<>();
        if (request.sinusTract() != null) symptoms.put("sinus_tract", request.sinusTract());
        clinicalRecords.put("symptoms", symptoms);
        if (request.daysSinceArthroplasty() != null) {
            clinicalRecords.put("infection_assessment", Map.of(
                    "onset_timing", request.daysSinceArthroplasty() < 90 ? "ACUTE_POSTOPERATIVE" : "CHRONIC"));
        }
        snapshot.put("clinical_records", clinicalRecords);

        Map<String, Object> labs = new LinkedHashMap<>();
        if (request.serumTests() != null) {
            putLab(labs, "crp", request.serumTests().crp(), "mg/L");
            putLab(labs, "esr", request.serumTests().esr(), "mm/h");
            putLab(labs, "d_dimer", request.serumTests().dDimer(), "ng/mL FEU");
        }
        if (request.synovialTests() != null) {
            putLab(labs, "synovial_wbc", request.synovialTests().wbc(), "cells/µL");
            putLab(labs, "synovial_pmn", request.synovialTests().pmn(), "%");
        }
        putQualitativeLab(labs, "leukocyte_esterase", leukocyteEsteraseValue(request.leukocyteEsterase()));
        putQualitativeLab(labs, "alpha_defensin", ternaryValue(request.alphaDefensin()));
        if (!labs.isEmpty()) {
            snapshot.put("lab_results", labs);
        }

        snapshot.put("culture_results", cultureResults(request.culturesPerformed(), request.cultureResult()));
        Map<String, Object> surgery = new LinkedHashMap<>();
        putTernaryEvidence(surgery, "positive_histology", request.histology());
        putTernaryEvidence(surgery, "intraoperative_purulence", request.purulence());
        if (!surgery.isEmpty()) {
            snapshot.put("surgeries", Map.of("items", List.of(surgery)));
        }
        return snapshot;
    }

    private Map<String, Object> cultureResults(Boolean performed, String result) {
        Map<String, Object> cultures = new LinkedHashMap<>();
        cultures.put("performed", performed);
        List<Map<String, Object>> items = new ArrayList<>();
        if (Boolean.TRUE.equals(performed)) {
            switch (normalize(result)) {
                case "negative" -> items.add(culture("NEGATIVE", null));
                case "singlepositive" -> items.add(culture("POSITIVE", "Tác nhân từ một mẫu"));
                case "multiplesameorganism" -> {
                    items.add(culture("POSITIVE", "Cùng tác nhân"));
                    items.add(culture("POSITIVE", "Cùng tác nhân"));
                }
                case "multipledifferentorganisms" -> {
                    items.add(culture("POSITIVE", "Tác nhân A"));
                    items.add(culture("POSITIVE", "Tác nhân B"));
                }
                default -> {
                    // Performed with no readable result remains unknown.
                }
            }
        }
        cultures.put("items", items);
        return cultures;
    }

    private Map<String, Object> culture(String status, String organism) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("result_status", status);
        if (organism != null) {
            item.put("organism_name", organism);
        }
        return item;
    }

    private void putLab(Map<String, Object> labs, String key, Double value, String unit) {
        if (value != null && Double.isFinite(value) && value >= 0) {
            labs.put(key, Map.of("value", value, "unit", unit));
        }
    }

    private void putQualitativeLab(Map<String, Object> labs, String key, String value) {
        if (value != null) {
            labs.put(key, Map.of("value", value));
        }
    }

    private void putTernaryEvidence(Map<String, Object> target, String key, String raw) {
        String normalized = normalize(raw);
        if ("positive".equals(normalized)) {
            target.put(key, true);
        } else if ("negative".equals(normalized)) {
            target.put(key, false);
        }
    }

    private String leukocyteEsteraseValue(String raw) {
        return switch (normalize(raw)) {
            case "negative" -> "NEGATIVE";
            case "trace" -> "TRACE";
            case "oneplus" -> "+";
            case "twoplus" -> "++";
            default -> null;
        };
    }

    private String ternaryValue(String raw) {
        return switch (normalize(raw)) {
            case "positive" -> "POSITIVE";
            case "negative" -> "NEGATIVE";
            default -> null;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> mapOfPresent(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value1 != null) result.put(key1, value1);
        if (value2 != null) result.put(key2, value2);
        return result;
    }
}
