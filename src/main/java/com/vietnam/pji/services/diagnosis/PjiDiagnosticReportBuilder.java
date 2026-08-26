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

    PjiDiagnosticRuleEngine.DiagnosticResult build(Map<String, Object> snapshot,
            PjiCultureEvidenceEvaluator.CultureEvidence culture,
            PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus,
            List<PjiDiagnosticCriteriaEvaluator.CriterionScore> scores,
            boolean majorCriteriaMet, PjiDiagnosticRuleEngine.EvaluationSummary summary) {
        int totalMinorScore = scores.stream()
                .mapToInt(PjiDiagnosticCriteriaEvaluator.CriterionScore::scoreAwarded).sum();
        String interpretation = summary.interpretation();
        String infectionClassification = infectionClassification(interpretation, summary.infectionClassification());
        Map<String, Object> majorCriteria = majorCriteria(culture, sinus, majorCriteriaMet);
        Map<String, Object> minorCriteria = minorCriteria(scores, totalMinorScore, majorCriteriaMet, summary);

        Map<String, Object> scoringSystem = new LinkedHashMap<>();
        scoringSystem.put("name", "2018 evidence-based and validated definition of PJI");
        scoringSystem.put("profile_id", "PJI_ICM_2018_VALIDATED_V1");
        scoringSystem.put("version", "ICM_2018_APP_REVISION_1");
        scoringSystem.put("definition_year", 2018);
        scoringSystem.put("implementation_version", "1.0.0");
        scoringSystem.put("chronic_threshold_profile", "VALIDATED_2018");
        scoringSystem.put("acute_threshold_profile", "PROPOSED_2018_UNVALIDATED");
        scoringSystem.put("authority_note", "Định nghĩa điểm PJI 2018 đã thẩm định cho khớp háng/gối mạn; không tự nhận là Unified PJI 2025.");
        scoringSystem.put("total_score", summary.decisionScore());
        scoringSystem.put("decision_stage", summary.decisionStage());
        scoringSystem.put("available_evidence_score", totalMinorScore);
        scoringSystem.put("preoperative_score", summary.preoperativeScore());
        scoringSystem.put("intraoperative_score", summary.intraoperativeScore());
        scoringSystem.put("combined_score", summary.combinedScore());
        scoringSystem.put("interpretation", interpretation);
        scoringSystem.put("interpretation_label", interpretationLabel(interpretation));
        scoringSystem.put("confidence_note", confidenceNote(interpretation, majorCriteriaMet, totalMinorScore));

        Map<String, Object> completeness = new LinkedHashMap<>();
        completeness.put("is_complete", summary.completeForConclusion());
        completeness.put("major_criteria_complete", summary.majorComplete());
        completeness.put("preoperative_complete", summary.preoperativeComplete());
        completeness.put("intraoperative_complete", summary.intraoperativeComplete());
        completeness.put("missing_evidence", summary.missingEvidence());
        completeness.put("missing_evidence_labels", summary.missingEvidence().stream()
                .map(this::missingEvidenceLabel).toList());
        completeness.put("limitations", summary.limitations());

        Map<String, Object> aiReasoning = new LinkedHashMap<>();
        aiReasoning.put("primary_diagnosis", primaryDiagnosis(snapshot, interpretation, infectionClassification));
        aiReasoning.put("infection_classification", infectionClassification);
        aiReasoning.put("infection_classification_reasoning",
                infectionClassificationReasoning(snapshot, interpretation, infectionClassification));
        aiReasoning.put("identified_organism", identifiedOrganism(culture));
        aiReasoning.put("reasoning_summary", reasoningSummary(interpretation, majorCriteriaMet, summary.decisionScore(),
                culture, summary.missingEvidence()));

        Map<String, Object> itemJson = new LinkedHashMap<>();
        itemJson.put("diagnostic_method", "RULE_BASED_BACKEND");
        itemJson.put("category", "DIAGNOSTIC_TEST");
        itemJson.put("scoring_system", scoringSystem);
        itemJson.put("data_completeness", completeness);
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
        assessment.put("data_complete", summary.completeForConclusion());
        assessment.put("missing_evidence", summary.missingEvidence());
        assessment.put("clinical_limitations", summary.limitations());

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("clinical_reasoning", aiReasoning.get("reasoning_summary"));
        explanation.put("diagnosis_summary", primaryDiagnosis(snapshot, interpretation, infectionClassification));
        explanation.put("diagnostic_basis", "Rule engine Backend áp dụng một hồ sơ quy tắc có phiên bản cho định nghĩa PJI 2018; dữ liệu thiếu giữ trạng thái chưa biết.");
        return new PjiDiagnosticRuleEngine.DiagnosticResult(
                "Chẩn đoán hệ thống - Đánh giá nhiễm trùng khớp nhân tạo theo tiêu chí ICM",
                itemJson, assessment, explanation);
    }

    private Map<String, Object> majorCriteria(PjiCultureEvidenceEvaluator.CultureEvidence culture,
            PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus, boolean majorCriteriaMet) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(majorCriterion("≥ 2 mẫu nuôi cấy dương tính cùng một vi khuẩn",
                culture.performed() == Boolean.TRUE ? culture.majorCriteriaMet() : null,
                culture.majorDetail(), culture.majorCriteriaMet()));
        items.add(majorCriterion("Đường rò thông với khớp giả", sinus.result(), sinus.detail(),
                sinus.result() == Boolean.TRUE));
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("note", "Tiêu chí chính có tính quyết định; nếu dương tính thì kết luận nhiễm trùng ngay.");
        criteria.put("items", items);
        criteria.put("major_criteria_met", majorCriteriaMet);
        criteria.put("major_criteria_conclusion", majorConclusion(majorCriteriaMet, sinus, culture));
        return criteria;
    }

    private Map<String, Object> minorCriteria(List<PjiDiagnosticCriteriaEvaluator.CriterionScore> scores,
            int totalMinorScore, boolean majorCriteriaMet, PjiDiagnosticRuleEngine.EvaluationSummary summary) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("note", "Tính điểm các tiêu chí phụ khi chưa thỏa tiêu chí chính; tiêu chí thiếu dữ liệu không được suy đoán.");
        criteria.put("items", scores.stream().map(PjiDiagnosticCriteriaEvaluator.CriterionScore::toMap).toList());
        criteria.put("total_minor_score", totalMinorScore);
        criteria.put("total_minor_score_note", minorScoreNote(totalMinorScore, majorCriteriaMet, summary));
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

    private String confidenceNote(String interpretation, boolean majorCriteriaMet, int score) {
        if ("INCOMPLETE".equals(interpretation)) {
            return "Chưa đủ dữ liệu bắt buộc; không được diễn giải điểm thiếu như bằng chứng âm tính.";
        }
        if ("NOT_APPLICABLE".equals(interpretation)) {
            return "Không áp dụng vì chưa có khớp háng/gối nhân tạo theo dữ liệu khai báo.";
        }
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
            if (sinus.result() == null || culture.performed() != Boolean.TRUE) {
                return "Chưa đủ dữ liệu để loại trừ tiêu chí chính; chưa được diễn giải như âm tính.";
            }
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

    private String minorScoreNote(int totalMinorScore, boolean majorCriteriaMet,
            PjiDiagnosticRuleEngine.EvaluationSummary summary) {
        if ("NOT_APPLICABLE".equals(summary.interpretation())) {
            return "Không chấm điểm quyết định vì định nghĩa PJI không áp dụng cho trường hợp này.";
        }
        if ("INCOMPLETE".equals(summary.interpretation())) {
            return totalMinorScore + "/" + MAX_MINOR_SCORE
                    + " điểm từ bằng chứng hiện có; dữ liệu còn thiếu nên đây không phải kết luận âm tính.";
        }
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
            case "INCOMPLETE" -> "Chưa đủ dữ liệu để phân loại nhiễm trùng khớp nhân tạo" + joint;
            case "NOT_APPLICABLE" -> "Không áp dụng định nghĩa PJI cho trường hợp chưa thay khớp háng/gối";
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
        if ("UNKNOWN".equals(infectionClassification)) {
            return "Đã phân loại PJI nhưng chưa đủ dữ liệu thời điểm khởi phát để phân loại cấp/mạn.";
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
        return switch (infectionClassification) {
            case "ACUTE" -> "cấp tính";
            case "CHRONIC" -> "mạn tính";
            default -> "chưa xác định cấp/mạn";
        };
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
            PjiCultureEvidenceEvaluator.CultureEvidence culture, List<String> missingEvidence) {
        List<String> parts = new ArrayList<>();
        if (majorCriteriaMet) {
            parts.add("Kết luận nhiễm trùng theo tiêu chí chính.");
        } else if ("INCOMPLETE".equals(interpretation)) {
            parts.add("Chưa đủ bằng chứng bắt buộc để phân loại; điểm khả dụng = " + score + ".");
        } else if ("NOT_APPLICABLE".equals(interpretation)) {
            parts.add("Không áp dụng định nghĩa PJI vì chưa có khớp háng/gối nhân tạo theo khai báo.");
        } else {
            parts.add("Không thỏa tiêu chí chính; điểm bằng chứng khả dụng = " + score + ".");
        }
        parts.add("Ngưỡng được diễn giải theo giai đoạn quyết định tiền phẫu hoặc phối hợp, không cộng dữ liệu thiếu như âm tính.");
        if (culture.topOrganism() != null) {
            parts.add("Tác nhân nổi bật: " + culture.topOrganism() + ".");
        }
        if (!missingEvidence.isEmpty()) {
            parts.add("Bằng chứng còn thiếu: " + String.join("; ", missingEvidence) + ".");
        }
        parts.add("Kết luận hiện tại: " + interpretationLabel(interpretation) + ".");
        return String.join(" ", parts);
    }

    private String interpretationLabel(String interpretation) {
        return switch (interpretation) {
            case "INFECTED" -> "Nhiễm trùng khớp nhân tạo (PJI)";
            case "INCONCLUSIVE" -> "Chưa kết luận được nhiễm trùng khớp nhân tạo (PJI)";
            case "INCOMPLETE" -> "Dữ liệu chưa đủ để phân loại PJI";
            case "NOT_APPLICABLE" -> "Không áp dụng định nghĩa PJI này";
            default -> "Không có bằng chứng nhiễm trùng khớp nhân tạo (PJI)";
        };
    }

    private String missingEvidenceLabel(String code) {
        return switch (code) {
            case "eligibility.previous_arthroplasty" -> "Chưa xác nhận người bệnh đã thay khớp háng/gối.";
            case "major.sinus_tract" -> "Chưa đánh giá đường rò thông với khớp nhân tạo.";
            case "major.culture_results" -> "Chưa thực hiện hoặc chưa có kết quả nuôi cấy đọc được.";
            default -> code.startsWith("preoperative.")
                    ? "Thiếu bằng chứng tiền phẫu: " + code.substring("preoperative.".length())
                    : code.startsWith("intraoperative.")
                            ? "Thiếu bằng chứng trong mổ: " + code.substring("intraoperative.".length())
                            : code;
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

    private Map<String, Object> majorCriterion(String criterion, Boolean result, String detail, boolean decisive) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("criterion", criterion);
        item.put("result", result);
        item.put("result_detail", detail);
        item.put("is_decisive", decisive);
        return item;
    }

}
