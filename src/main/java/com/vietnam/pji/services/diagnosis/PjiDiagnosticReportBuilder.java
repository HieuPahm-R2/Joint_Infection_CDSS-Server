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
    private static final int MAX_MINOR_SCORE = 16;

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
            boolean majorCriteriaMet, int totalMinorScore, String interpretation, String clinicalPhase) {
        String infectionClassification = infectionClassification(interpretation, clinicalPhase);
        Map<String, Object> majorCriteria = majorCriteria(culture, sinus, majorCriteriaMet);
        Map<String, Object> minorCriteria = minorCriteria(scores, totalMinorScore, majorCriteriaMet);

        Map<String, Object> scoringSystem = new LinkedHashMap<>();
        scoringSystem.put("name", "Tiêu chí chẩn đoán PJI theo ICM");
        scoringSystem.put("version", "Tính theo quy tắc tại Backend (điểm ICM 2018, biện pháp bảo đảm quy trình chẩn đoán ICM 2025)");
        scoringSystem.put("total_score", totalMinorScore);
        scoringSystem.put("interpretation", interpretation);
        scoringSystem.put("interpretation_label", interpretationLabel(interpretation));
        scoringSystem.put("confidence_note", confidenceNote(majorCriteriaMet, totalMinorScore));

        Map<String, Object> aiReasoning = new LinkedHashMap<>();
        aiReasoning.put("primary_diagnosis", primaryDiagnosis(snapshot, interpretation, infectionClassification));
        aiReasoning.put("infection_classification", infectionClassification);
        aiReasoning.put("infection_classification_reasoning",
                infectionClassificationReasoning(snapshot, interpretation, infectionClassification));
        aiReasoning.put("identified_organism", identifiedOrganism(culture));
        aiReasoning.put("reasoning_summary", reasoningSummary(interpretation, majorCriteriaMet, totalMinorScore, culture));

        Map<String, Object> itemJson = new LinkedHashMap<>();
        itemJson.put("diagnostic_method", "RULE_BASED_BACKEND");
        itemJson.put("category", "DIAGNOSTIC_TEST");
        itemJson.put("scoring_system", scoringSystem);
        itemJson.put("major_criteria", majorCriteria);
        itemJson.put("minor_criteria_scoring", minorCriteria);
        itemJson.put("supporting_evidence", supportingEvidence(snapshot));
        itemJson.put("ai_reasoning", aiReasoning);

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("overall_assessment", primaryDiagnosis(snapshot, interpretation, infectionClassification));
        assessment.put("pji_probability", interpretation);
        assessment.put("pji_probability_label", interpretationLabel(interpretation));
        assessment.put("infection_classification", infectionClassification);
        assessment.put("diagnostic_method", "RULE_BASED_BACKEND");
        assessment.put("major_criteria_met", majorCriteriaMet);
        assessment.put("minor_score", totalMinorScore);

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("clinical_reasoning", aiReasoning.get("reasoning_summary"));
        explanation.put("diagnosis_summary", primaryDiagnosis(snapshot, interpretation, infectionClassification));
        explanation.put("diagnostic_basis", "Rule engine Backend áp dụng tiêu chí chính rõ ràng và chấm điểm tiêu chí phụ theo ICM.");
        return new PjiDiagnosticRuleEngine.DiagnosticResult(
                "Chẩn đoán hệ thống - Đánh giá nhiễm trùng khớp nhân tạo theo tiêu chí ICM",
                itemJson, assessment, explanation);
    }

    private Map<String, Object> majorCriteria(PjiCultureEvidenceEvaluator.CultureEvidence culture,
            PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus, boolean majorCriteriaMet) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(majorCriterion("≥ 2 mẫu nuôi cấy dương tính cùng một vi khuẩn", culture.majorCriteriaMet(),
                culture.majorDetail(), culture.majorCriteriaMet()));
        items.add(majorCriterion("Đường rò thông với khớp giả", sinus.result() == Boolean.TRUE, sinus.detail(),
                sinus.result() == Boolean.TRUE));
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("note", "Tiêu chí chính có tính quyết định; nếu dương tính thì kết luận nhiễm trùng ngay.");
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

    private String confidenceNote(boolean majorCriteriaMet, int score) {
        if (majorCriteriaMet) {
            return "Độ tin cậy cao vì đã thỏa tiêu chí chính.";
        }
        if (score >= INFECTED_SCORE_THRESHOLD) {
            return "Điểm tiêu chí phụ ≥6, phù hợp kết luận nhiễm trùng.";
        }
        if (score >= INCONCLUSIVE_SCORE_MIN) {
            return "Điểm tiêu chí phụ 4-5, chưa kết luận được.";
        }
        return "Điểm tiêu chí phụ ≤3, chưa ủng hộ PJI theo dữ liệu hiện có.";
    }

    private String majorConclusion(boolean majorCriteriaMet, PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus,
            PjiCultureEvidenceEvaluator.CultureEvidence culture) {
        if (!majorCriteriaMet) {
            return "Chưa thỏa tiêu chí chính; diễn giải dựa trên tổng điểm tiêu chí phụ.";
        }
        List<String> reasons = new ArrayList<>();
        if (culture.majorCriteriaMet()) {
            reasons.add("≥2 mẫu nuôi cấy cùng tác nhân");
        }
        if (sinus.result() == Boolean.TRUE) {
            reasons.add("đường rò thông với khớp giả");
        }
        return "Đã thỏa tiêu chí chính (" + String.join("; ", reasons) + ") → kết luận nhiễm trùng.";
    }

    private String minorScoreNote(int totalMinorScore, boolean majorCriteriaMet) {
        String base = totalMinorScore >= INFECTED_SCORE_THRESHOLD ? totalMinorScore + "/" + MAX_MINOR_SCORE + " điểm tiêu chí phụ khả dụng → ≥6, kết luận nhiễm trùng."
                : totalMinorScore >= INCONCLUSIVE_SCORE_MIN ? totalMinorScore + "/" + MAX_MINOR_SCORE + " điểm tiêu chí phụ khả dụng → 4-5, chưa kết luận được."
                : totalMinorScore + "/" + MAX_MINOR_SCORE + " điểm tiêu chí phụ khả dụng → ≤3, chưa có bằng chứng nhiễm trùng nếu không có tiêu chí chính.";
        return majorCriteriaMet ? base + " Tuy nhiên tiêu chí chính đã đủ để kết luận nhiễm trùng." : base;
    }

    private String primaryDiagnosis(Map<String, Object> snapshot, String interpretation,
            String infectionClassification) {
        String joint = snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "prosthesis_joint")
                .map(Object::toString).filter(value -> !value.isBlank()).map(value -> " " + value.replace('_', ' ')).orElse("");
        return switch (interpretation) {
            case "INFECTED" -> "Nhiễm trùng khớp nhân tạo" + joint + " "
                    + infectionClassificationLabel(infectionClassification);
            case "INCONCLUSIVE" -> "Chưa xác định nhiễm trùng khớp nhân tạo" + joint;
            default -> "Chưa đủ bằng chứng nhiễm trùng khớp nhân tạo" + joint;
        };
    }

    private String infectionClassification(String interpretation, String clinicalPhase) {
        return "INFECTED".equals(interpretation) ? clinicalPhase : "NOT_APPLICABLE";
    }

    private String infectionClassificationReasoning(Map<String, Object> snapshot, String interpretation,
            String infectionClassification) {
        if (!"INFECTED".equals(interpretation)) {
            return "Không phân loại cấp/mạn vì kết luận hiện tại chưa xác định nhiễm trùng.";
        }
        List<String> facts = new ArrayList<>();
        String onsetTiming = snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "onset_timing")
                .or(() -> snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "suspected_infection_type"))
                .or(() -> snapshotReader.getNested(snapshot, "clinical_records", "onset_timing"))
                .map(Object::toString).filter(value -> !value.isBlank()).orElse("không ghi nhận");
        facts.add("Phân loại " + infectionClassificationLabel(infectionClassification)
                + " theo thời điểm khởi phát: " + onsetTiming);
        snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "suspected_transmission_route")
                .ifPresent(value -> facts.add("đường lây nhiễm nghi ngờ: " + value));
        snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "hematogenous_suspected")
                .ifPresent(value -> facts.add("nghi đường máu: " + value));
        snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "implant_stability")
                .ifPresent(value -> facts.add("ổn định implant: " + value));
        return String.join("; ", facts) + ".";
    }

    private String infectionClassificationLabel(String infectionClassification) {
        return "ACUTE".equals(infectionClassification) ? "cấp tính" : "mạn tính";
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
        parts.add(majorCriteriaMet ? "Kết luận nhiễm trùng theo tiêu chí chính."
                : "Không thỏa tiêu chí chính; phân loại theo tổng điểm tiêu chí phụ = " + score + ".");
        parts.add("Ngưỡng diễn giải: ≥6 điểm là nhiễm trùng, 4-5 điểm chưa kết luận, ≤3 điểm không nhiễm trùng.");
        if (culture.topOrganism() != null) {
            parts.add("Tác nhân nổi bật: " + culture.topOrganism() + ".");
        }
        parts.add("Kết luận hiện tại: " + interpretationLabel(interpretation) + ".");
        return String.join(" ", parts);
    }

    private String interpretationLabel(String interpretation) {
        return switch (interpretation) {
            case "INFECTED" -> "Nhiễm trùng khớp nhân tạo (PJI)";
            case "INCONCLUSIVE" -> "Chưa kết luận được nhiễm trùng khớp nhân tạo (PJI)";
            default -> "Không có bằng chứng nhiễm trùng khớp nhân tạo (PJI)";
        };
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

    private Map<String, Object> majorCriterion(String criterion, boolean result, String detail, boolean decisive) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("criterion", criterion);
        item.put("result", result);
        item.put("result_detail", detail);
        item.put("is_decisive", decisive);
        return item;
    }

}
