package com.vietnam.pji.services.diagnosis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds the stable API report from already-evaluated diagnostic evidence. */
@Component
class PjiDiagnosticReportBuilder {

    private static final int INFECTED_SCORE_THRESHOLD = 6;
    private static final int INCONCLUSIVE_SCORE_MIN = 4;

    private final PjiDiagnosticSnapshotReader snapshotReader;

    PjiDiagnosticReportBuilder(PjiDiagnosticSnapshotReader snapshotReader) {
        this.snapshotReader = snapshotReader;
    }

    String interpret(boolean majorCriteriaMet, int totalMinorScore) {
        if (majorCriteriaMet || totalMinorScore >= INFECTED_SCORE_THRESHOLD) {
            return "INFECTED";
        }
        return totalMinorScore >= INCONCLUSIVE_SCORE_MIN ? "INCONCLUSIVE" : "NOT_INFECTED";
    }

    PjiDiagnosticRuleEngine.DiagnosticResult build(Map<String, Object> snapshot,
            PjiCultureEvidenceEvaluator.CultureEvidence culture,
            PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus,
            List<PjiDiagnosticCriteriaEvaluator.CriterionScore> scores,
            boolean majorCriteriaMet, int totalMinorScore, String interpretation) {
        List<Map<String, Object>> warnings = buildWarnings(snapshot, culture, scores);
        Map<String, Object> majorCriteria = majorCriteria(culture, sinus, majorCriteriaMet);
        Map<String, Object> minorCriteria = minorCriteria(scores, totalMinorScore, majorCriteriaMet);

        Map<String, Object> scoringSystem = new LinkedHashMap<>();
        scoringSystem.put("name", "ICM PJI Diagnostic Criteria");
        scoringSystem.put("version", "Rule-based backend calculation (ICM 2018 score, ICM 2025 diagnostic-process safeguards)");
        scoringSystem.put("total_score", totalMinorScore);
        scoringSystem.put("interpretation", interpretation);
        scoringSystem.put("confidence_note", confidenceNote(majorCriteriaMet, totalMinorScore, missingCount(scores)));

        Map<String, Object> aiReasoning = new LinkedHashMap<>();
        aiReasoning.put("primary_diagnosis", primaryDiagnosis(snapshot, interpretation));
        aiReasoning.put("infection_classification", infectionClassification(snapshot));
        aiReasoning.put("infection_classification_reasoning", infectionClassificationReasoning(snapshot));
        aiReasoning.put("identified_organism", identifiedOrganism(culture));
        aiReasoning.put("reasoning_summary", reasoningSummary(interpretation, majorCriteriaMet, totalMinorScore, culture));
        aiReasoning.put("warnings", warnings);

        Map<String, Object> itemJson = new LinkedHashMap<>();
        itemJson.put("diagnostic_method", "RULE_BASED_BACKEND");
        itemJson.put("category", "DIAGNOSTIC_TEST");
        itemJson.put("scoring_system", scoringSystem);
        itemJson.put("major_criteria", majorCriteria);
        itemJson.put("minor_criteria_scoring", minorCriteria);
        itemJson.put("supporting_evidence", supportingEvidence(snapshot));
        itemJson.put("ai_reasoning", aiReasoning);

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("overall_assessment", primaryDiagnosis(snapshot, interpretation));
        assessment.put("pji_probability", interpretation);
        assessment.put("diagnostic_method", "RULE_BASED_BACKEND");
        assessment.put("major_criteria_met", majorCriteriaMet);
        assessment.put("minor_score", totalMinorScore);

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("clinical_reasoning", aiReasoning.get("reasoning_summary"));
        explanation.put("diagnosis_summary", primaryDiagnosis(snapshot, interpretation));
        explanation.put("diagnostic_basis", "Backend rule engine using explicit major criteria and ICM-style minor scoring.");
        return new PjiDiagnosticRuleEngine.DiagnosticResult(
                "Chẩn đoán hệ thống - Đánh giá nhiễm trùng khớp giả theo tiêu chí ICM",
                itemJson, assessment, explanation, warnings);
    }

    private Map<String, Object> majorCriteria(PjiCultureEvidenceEvaluator.CultureEvidence culture,
            PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus, boolean majorCriteriaMet) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(majorCriterion("≥ 2 mẫu nuôi cấy dương tính cùng một vi khuẩn", culture.majorCriteriaMet(),
                culture.majorDetail(), culture.majorCriteriaMet()));
        items.add(majorCriterion("Đường rò thông với khớp giả", sinus.result() == Boolean.TRUE, sinus.detail(),
                sinus.result() == Boolean.TRUE));
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("note", "Tiêu chí chính có tính quyết định; nếu dương tính thì kết luận INFECTED ngay.");
        criteria.put("items", items);
        criteria.put("major_criteria_met", majorCriteriaMet);
        criteria.put("major_criteria_conclusion", majorConclusion(majorCriteriaMet, sinus, culture));
        return criteria;
    }

    private Map<String, Object> minorCriteria(List<PjiDiagnosticCriteriaEvaluator.CriterionScore> scores,
            int totalMinorScore, boolean majorCriteriaMet) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("note", "Tính điểm các tiêu chí phụ khi chưa thỏa tiêu chí chính; tiêu chí thiếu dữ liệu không được suy đoán.");
        criteria.put("items", scores.stream().map(PjiDiagnosticCriteriaEvaluator.CriterionScore::toMap).toList());
        criteria.put("total_minor_score", totalMinorScore);
        criteria.put("total_minor_score_note", minorScoreNote(totalMinorScore, majorCriteriaMet));
        return criteria;
    }

    private Map<String, Object> supportingEvidence(Map<String, Object> snapshot) {
        Map<String, Object> supporting = new LinkedHashMap<>();
        snapshotReader.findLab(snapshot, "serum_IL6").ifPresent(il6 -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", "Serum IL-6");
            item.put("value", il6.value());
            item.put("unit", il6.unit());
            item.put("note", "Dữ liệu hỗ trợ viêm/nhiễm trùng; không dùng làm tiêu chí quyết định trong điểm core ở phiên bản này.");
            supporting.put("serum_il6", item);
        });
        return supporting;
    }

    private List<Map<String, Object>> buildWarnings(Map<String, Object> snapshot,
            PjiCultureEvidenceEvaluator.CultureEvidence culture,
            List<PjiDiagnosticCriteriaEvaluator.CriterionScore> scores) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        Optional<Object> allergy = snapshotReader.getNested(snapshot, "medical_history", "allergies", "is_allergy");
        if (allergy.map(snapshotReader::asBoolean).orElse(false)) {
            String note = snapshotReader.getNested(snapshot, "medical_history", "allergies", "allergy_note")
                    .map(Object::toString).orElse("Có tiền sử dị ứng thuốc.");
            warnings.add(warning("ALLERGY_ALERT", "HIGH", note));
        }
        if (culture.antibioticsBefore()) {
            warnings.add(warning("DATA_QUALITY", "MEDIUM",
                    "Có mẫu nuôi cấy được ghi nhận sau khi đã dùng kháng sinh; kết quả âm tính cần diễn giải thận trọng."));
        }
        long missingCritical = scores.stream().filter(score -> score.result() == null)
                .filter(score -> score.scoreWeight() >= 2).count();
        if (missingCritical > 0) {
            warnings.add(warning("DATA_COMPLETENESS", "MEDIUM", "Còn thiếu " + missingCritical
                    + " tiêu chí chẩn đoán quan trọng; hệ thống không suy đoán các tiêu chí này."));
        }
        return warnings;
    }

    private String confidenceNote(boolean majorCriteriaMet, int score, int missingCount) {
        if (majorCriteriaMet) {
            return "Độ tin cậy cao vì đã thỏa tiêu chí chính.";
        }
        String missing = missingCount > 0 ? "; còn " + missingCount + " tiêu chí thiếu dữ liệu" : "";
        if (score >= INFECTED_SCORE_THRESHOLD) {
            return "Điểm minor ≥6, phù hợp INFECTED" + missing + ".";
        }
        if (score >= INCONCLUSIVE_SCORE_MIN) {
            return "Điểm minor 4-5, kết luận INCONCLUSIVE và cần bổ sung dữ liệu" + missing + ".";
        }
        return "Điểm minor ≤3, chưa ủng hộ PJI theo dữ liệu hiện có" + missing + ".";
    }

    private String majorConclusion(boolean majorCriteriaMet, PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus,
            PjiCultureEvidenceEvaluator.CultureEvidence culture) {
        if (!majorCriteriaMet) {
            return "Chưa thỏa tiêu chí chính; diễn giải dựa trên tổng điểm minor và dữ liệu còn thiếu.";
        }
        List<String> reasons = new ArrayList<>();
        if (culture.majorCriteriaMet()) {
            reasons.add("≥2 mẫu nuôi cấy cùng tác nhân");
        }
        if (sinus.result() == Boolean.TRUE) {
            reasons.add("đường rò thông với khớp giả");
        }
        return "Đã thỏa tiêu chí chính (" + String.join("; ", reasons) + ") → kết luận INFECTED.";
    }

    private String minorScoreNote(int totalMinorScore, boolean majorCriteriaMet) {
        String base = totalMinorScore >= INFECTED_SCORE_THRESHOLD ? totalMinorScore + "/20 điểm minor khả dụng → ≥6, phân loại INFECTED."
                : totalMinorScore >= INCONCLUSIVE_SCORE_MIN ? totalMinorScore + "/20 điểm minor khả dụng → 4-5, phân loại INCONCLUSIVE."
                : totalMinorScore + "/20 điểm minor khả dụng → ≤3, phân loại NOT_INFECTED nếu không có tiêu chí chính.";
        return majorCriteriaMet ? base + " Tuy nhiên tiêu chí chính đã đủ để kết luận INFECTED." : base;
    }

    private String primaryDiagnosis(Map<String, Object> snapshot, String interpretation) {
        String joint = snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "prosthesis_joint")
                .map(Object::toString).filter(value -> !value.isBlank()).map(value -> " " + value.replace('_', ' ')).orElse("");
        return switch (interpretation) {
            case "INFECTED" -> "Nhiễm trùng khớp giả" + joint;
            case "INCONCLUSIVE" -> "Chưa xác định nhiễm trùng khớp giả" + joint;
            default -> "Chưa đủ bằng chứng nhiễm trùng khớp giả" + joint;
        };
    }

    private String infectionClassification(Map<String, Object> snapshot) {
        return snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "onset_timing")
                .or(() -> snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "suspected_infection_type"))
                .map(Object::toString).filter(value -> !value.isBlank()).orElse("UNKNOWN");
    }

    private String infectionClassificationReasoning(Map<String, Object> snapshot) {
        List<String> facts = new ArrayList<>();
        facts.add("Thời điểm khởi phát so với phẫu thuật gần nhất: " + infectionClassification(snapshot));
        snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "suspected_transmission_route")
                .ifPresent(value -> facts.add("đường lây nhiễm nghi ngờ: " + value));
        snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "hematogenous_suspected")
                .ifPresent(value -> facts.add("nghi đường máu: " + value));
        snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "implant_stability")
                .ifPresent(value -> facts.add("ổn định implant: " + value));
        return String.join("; ", facts) + ".";
    }

    private Map<String, Object> identifiedOrganism(PjiCultureEvidenceEvaluator.CultureEvidence culture) {
        Map<String, Object> organism = new LinkedHashMap<>();
        if (culture.topOrganism() == null || culture.topOrganism().isBlank()) {
            organism.put("name", "Chưa xác định");
            organism.put("resistance_profile", "UNKNOWN");
            organism.put("resistance_detail", "Chưa có mẫu nuôi cấy dương tính hoặc chưa định danh tác nhân.");
            organism.put("biofilm_forming", false);
            organism.put("virulence_note", "Không suy đoán tác nhân khi thiếu bằng chứng vi sinh.");
            return organism;
        }
        organism.put("name", culture.topOrganism());
        organism.put("resistance_profile", resistanceProfile(culture.topOrganism(), culture.sensitivities()));
        organism.put("resistance_detail", resistanceDetail(culture.sensitivities()));
        organism.put("biofilm_forming", true);
        organism.put("virulence_note", virulenceNote(culture.topOrganism()));
        return organism;
    }

    private String reasoningSummary(String interpretation, boolean majorCriteriaMet, int score,
            PjiCultureEvidenceEvaluator.CultureEvidence culture) {
        List<String> parts = new ArrayList<>();
        parts.add(majorCriteriaMet ? "Kết luận INFECTED theo tiêu chí chính."
                : "Không thỏa tiêu chí chính; phân loại theo điểm minor = " + score + ".");
        parts.add("Ngưỡng diễn giải: ≥6 INFECTED, 4-5 INCONCLUSIVE, ≤3 NOT_INFECTED.");
        if (culture.topOrganism() != null) {
            parts.add("Tác nhân nổi bật: " + culture.topOrganism() + ".");
        }
        parts.add("Kết luận hiện tại: " + interpretation + ".");
        return String.join(" ", parts);
    }

    private String resistanceProfile(String organism, List<Map<String, Object>> sensitivities) {
        String normalized = PjiDiagnosticSnapshotReader.normalizeToken(organism);
        boolean staphAureus = normalized.contains("staphylococcusaureus") || normalized.contains("staphaureus")
                || normalized.contains("saureus");
        if (!staphAureus) {
            return "Theo kháng sinh đồ";
        }
        Optional<String> oxacillin = sensitivityCode(sensitivities, Set.of("oxacillin", "methicillin", "cefoxitin"));
        if (oxacillin.map("R"::equals).orElse(false)) {
            return "MRSA";
        }
        if (oxacillin.map("S"::equals).orElse(false)) {
            return "MSSA";
        }
        return "Staphylococcus aureus - chưa rõ methicillin";
    }

    private String resistanceDetail(List<Map<String, Object>> sensitivities) {
        if (sensitivities == null || sensitivities.isEmpty()) {
            return "Chưa có kháng sinh đồ.";
        }
        List<String> items = new ArrayList<>();
        for (Map<String, Object> sensitivity : sensitivities) {
            String name = snapshotReader.firstText(sensitivity.get("antibiotic_name"), sensitivity.get("antibioticName"));
            String code = snapshotReader.firstText(sensitivity.get("sensitivity_code"), sensitivity.get("sensitivityCode"));
            String mic = snapshotReader.firstText(sensitivity.get("mic_value"), sensitivity.get("micValue"));
            if (name != null && code != null) {
                items.add(name + " " + code + (mic != null && !mic.isBlank() ? " (MIC " + mic + ")" : ""));
            }
        }
        return items.isEmpty() ? "Chưa có kháng sinh đồ đọc được." : String.join("; ", items) + ".";
    }

    private Optional<String> sensitivityCode(List<Map<String, Object>> sensitivities, Set<String> antibioticAliases) {
        if (sensitivities == null) {
            return Optional.empty();
        }
        for (Map<String, Object> sensitivity : sensitivities) {
            String name = PjiDiagnosticSnapshotReader.normalizeToken(snapshotReader.firstText(
                    sensitivity.get("antibiotic_name"), sensitivity.get("antibioticName")));
            if (antibioticAliases.stream().map(PjiDiagnosticSnapshotReader::normalizeToken).anyMatch(name::contains)) {
                String code = snapshotReader.firstText(sensitivity.get("sensitivity_code"), sensitivity.get("sensitivityCode"));
                if (code != null) {
                    return Optional.of(code.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return Optional.empty();
    }

    private String virulenceNote(String organism) {
        return PjiDiagnosticSnapshotReader.normalizeToken(organism).contains("staphylococcus")
                ? "Staphylococcus spp. là tác nhân PJI thường gặp và có khả năng tạo biofilm trên implant."
                : "Tác nhân được định danh từ mẫu dương tính; cần đối chiếu bối cảnh lâm sàng và nguy cơ nhiễm bẩn mẫu.";
    }

    private int missingCount(List<PjiDiagnosticCriteriaEvaluator.CriterionScore> scores) {
        return (int) scores.stream().filter(score -> score.result() == null).count();
    }

    private Map<String, Object> majorCriterion(String criterion, boolean result, String detail, boolean decisive) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("criterion", criterion);
        item.put("result", result);
        item.put("result_detail", detail);
        item.put("is_decisive", decisive);
        return item;
    }

    private Map<String, Object> warning(String type, String severity, String message) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("type", type);
        warning.put("severity", severity);
        warning.put("message", message);
        return warning;
    }
}
