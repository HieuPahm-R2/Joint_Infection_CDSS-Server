package com.vietnam.pji.services.diagnosis;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic PJI diagnostic calculator.
 *
 * <p>The AI worker may still reason about treatment, but the persisted
 * DIAGNOSTIC_TEST item must be generated from source-of-truth clinical data.
 * This keeps the user-facing diagnosis tied to explicit ICM-style rules rather
 * than free-form LLM output.
 */
@Service
public class PjiDiagnosticRuleEngine {

    private static final int INFECTED_SCORE_THRESHOLD = 6;
    private static final int INCONCLUSIVE_SCORE_MIN = 4;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:[\\.,]\\d+)?");

    private static final Map<String, LabAlias> LAB_ALIASES = Map.ofEntries(
            Map.entry("serum_CRP", new LabAlias(
                    Set.of("htextracrp"),
                    Set.of("crp"),
                    Set.of("hematology_tests", "biochemical_data", "latest"))),
            Map.entry("serum_ESR", new LabAlias(
                    Set.of("ht7"),
                    Set.of("maulang", "esr", "tocdomaulang"),
                    Set.of("hematology_tests", "latest"))),
            Map.entry("serum_D_Dimer", new LabAlias(
                    Set.of("ht17"),
                    Set.of("ddimer"),
                    Set.of("hematology_tests", "latest"))),
            Map.entry("synovial_WBC", new LabAlias(
                    Set.of("fa3"),
                    Set.of("synovialwbc", "bachcaudich"),
                    Set.of("fluid_analysis", "latest"))),
            Map.entry("synovial_PMN", new LabAlias(
                    Set.of("fa6"),
                    Set.of("synovialpmn", "pmndich"),
                    Set.of("fluid_analysis", "latest"))),
            Map.entry("synovial_CRP", new LabAlias(
                    Set.of("fa5"),
                    Set.of("crpdich", "synovialcrp", "dinhluongcrpdich"),
                    Set.of("fluid_analysis", "latest"))),
            Map.entry("synovial_alpha_defensin", new LabAlias(
                    Set.of("faextraalphadefensin", "ht19"),
                    Set.of("alphadefensin"),
                    Set.of("fluid_analysis", "hematology_tests", "latest"))),
            Map.entry("synovial_LE", new LabAlias(
                    Set.of("faextraleukocyteesterase", "ht15"),
                    Set.of("leukocyteesterase"),
                    Set.of("fluid_analysis", "hematology_tests", "latest"))),
            Map.entry("serum_IL6", new LabAlias(
                    Set.of("ht18"),
                    Set.of("il6"),
                    Set.of("hematology_tests", "latest"))));

    public DiagnosticResult evaluate(Map<String, Object> snapshot) {
        Map<String, Object> safeSnapshot = snapshot != null ? snapshot : Map.of();

        CultureEvidence culture = evaluateCultures(safeSnapshot);
        MajorCriterion sinus = evaluateSinusTract(safeSnapshot);
        boolean majorCriteriaMet = sinus.result == Boolean.TRUE || culture.majorCriteriaMet();

        List<Map<String, Object>> majorItems = new ArrayList<>();
        majorItems.add(majorCriterion(
                "≥ 2 mẫu nuôi cấy dương tính cùng một vi khuẩn",
                culture.majorCriteriaMet(),
                culture.majorDetail(),
                culture.majorCriteriaMet()));
        majorItems.add(majorCriterion(
                "Đường rò thông với khớp giả",
                sinus.result == Boolean.TRUE,
                sinus.detail,
                sinus.result == Boolean.TRUE));

        List<CriterionScore> scores = new ArrayList<>();
        scores.add(evaluateSerumCrpOrDimer(safeSnapshot));
        scores.add(evaluateNumericLab(
                safeSnapshot,
                "serum_ESR",
                "Tốc độ máu lắng tăng ESR (>30 mm/h)",
                1,
                30.0,
                "mm/h"));
        scores.add(evaluateSynovialWbcOrLe(safeSnapshot));
        scores.add(evaluateNumericLab(
                safeSnapshot,
                "synovial_PMN",
                "Synovial PMN% (>80%)",
                2,
                80.0,
                "%"));
        scores.add(evaluatePositiveAlphaDefensin(safeSnapshot));
        scores.add(evaluateNumericLab(
                safeSnapshot,
                "synovial_CRP",
                "Synovial CRP (>6.9 mg/L)",
                1,
                6.9,
                "mg/L"));
        scores.add(evaluateSinglePositiveCulture(culture));
        scores.add(evaluateHistology(safeSnapshot));
        scores.add(evaluatePurulence(safeSnapshot));

        int totalMinorScore = scores.stream().mapToInt(CriterionScore::scoreAwarded).sum();
        String interpretation = interpret(majorCriteriaMet, totalMinorScore);

        Map<String, Object> scoringSystem = new LinkedHashMap<>();
        scoringSystem.put("name", "ICM PJI Diagnostic Criteria");
        scoringSystem.put("version", "Rule-based backend calculation (ICM 2018 score, ICM 2025 diagnostic-process safeguards)");
        scoringSystem.put("total_score", totalMinorScore);
        scoringSystem.put("interpretation", interpretation);
        scoringSystem.put("confidence_note", confidenceNote(majorCriteriaMet, totalMinorScore, missingCount(scores)));

        Map<String, Object> majorCriteria = new LinkedHashMap<>();
        majorCriteria.put("note", "Tiêu chí chính có tính quyết định; nếu dương tính thì kết luận INFECTED ngay.");
        majorCriteria.put("items", majorItems);
        majorCriteria.put("major_criteria_met", majorCriteriaMet);
        majorCriteria.put("major_criteria_conclusion", majorConclusion(majorCriteriaMet, sinus, culture));

        Map<String, Object> minorCriteria = new LinkedHashMap<>();
        minorCriteria.put("note", "Tính điểm các tiêu chí phụ khi chưa thỏa tiêu chí chính; tiêu chí thiếu dữ liệu không được suy đoán.");
        minorCriteria.put("items", scores.stream().map(CriterionScore::toMap).toList());
        minorCriteria.put("total_minor_score", totalMinorScore);
        minorCriteria.put("total_minor_score_note", minorScoreNote(totalMinorScore, majorCriteriaMet));

        List<Map<String, Object>> warnings = buildWarnings(safeSnapshot, culture, scores);

        Map<String, Object> aiReasoning = new LinkedHashMap<>();
        aiReasoning.put("primary_diagnosis", primaryDiagnosis(safeSnapshot, interpretation));
        aiReasoning.put("infection_classification", infectionClassification(safeSnapshot));
        aiReasoning.put("infection_classification_reasoning", infectionClassificationReasoning(safeSnapshot));
        aiReasoning.put("identified_organism", identifiedOrganism(culture));
        aiReasoning.put("reasoning_summary", reasoningSummary(interpretation, majorCriteriaMet, totalMinorScore, culture));
        aiReasoning.put("warnings", warnings);

        Map<String, Object> itemJson = new LinkedHashMap<>();
        itemJson.put("diagnostic_method", "RULE_BASED_BACKEND");
        itemJson.put("category", "DIAGNOSTIC_TEST");
        itemJson.put("scoring_system", scoringSystem);
        itemJson.put("major_criteria", majorCriteria);
        itemJson.put("minor_criteria_scoring", minorCriteria);
        itemJson.put("supporting_evidence", supportingEvidence(safeSnapshot));
        itemJson.put("ai_reasoning", aiReasoning);

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("overall_assessment", primaryDiagnosis(safeSnapshot, interpretation));
        assessment.put("pji_probability", interpretation);
        assessment.put("diagnostic_method", "RULE_BASED_BACKEND");
        assessment.put("major_criteria_met", majorCriteriaMet);
        assessment.put("minor_score", totalMinorScore);

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("clinical_reasoning", aiReasoning.get("reasoning_summary"));
        explanation.put("diagnosis_summary", primaryDiagnosis(safeSnapshot, interpretation));
        explanation.put("diagnostic_basis", "Backend rule engine using explicit major criteria and ICM-style minor scoring.");

        return new DiagnosticResult(
                "Chẩn đoán hệ thống - Đánh giá nhiễm trùng khớp giả theo tiêu chí ICM",
                itemJson,
                assessment,
                explanation,
                warnings);
    }

    private MajorCriterion evaluateSinusTract(Map<String, Object> snapshot) {
        Optional<Object> value = getNested(snapshot, "clinical_records", "symptoms", "sinus_tract");
        if (value.isEmpty()) {
            return new MajorCriterion(null, "Chưa có dữ liệu đánh giá đường rò.");
        }
        Boolean verdict = asBoolean(value.get());
        if (verdict == null) {
            return new MajorCriterion(null, "Giá trị đường rò không đọc được: " + value.get());
        }
        return new MajorCriterion(verdict, verdict
                ? "Ghi nhận có đường rò thông với khớp giả."
                : "Không ghi nhận đường rò thông với khớp giả.");
    }

    private CriterionScore evaluateSerumCrpOrDimer(Map<String, Object> snapshot) {
        LabDatum crp = findLab(snapshot, "serum_CRP").orElse(null);
        LabDatum dDimer = findLab(snapshot, "serum_D_Dimer").orElse(null);

        Double crpValue = numericValue(crp);
        Double dDimerNgMl = dDimerNgMl(dDimer);

        Boolean crpPositive = crpValue != null ? crpValue > 10.0 : null;
        Boolean dDimerPositive = dDimerNgMl != null ? dDimerNgMl > 860.0 : null;
        Boolean result = anyPositiveOrNull(crpPositive, dDimerPositive);

        List<String> details = new ArrayList<>();
        details.add(crpValue != null
                ? "CRP = " + formatNumber(crpValue) + unitSuffix(crp) + (crpPositive ? " (>10)" : " (≤10)")
                : "CRP huyết thanh: chưa có dữ liệu");
        details.add(dDimerNgMl != null
                ? "D-Dimer = " + formatNumber(dDimerNgMl) + " ng/mL FEU" + (dDimerPositive ? " (>860)" : " (≤860)")
                : "D-Dimer: chưa có dữ liệu");

        return new CriterionScore(
                "Serum CRP (>10 mg/L) hoặc D-Dimer (>860 ng/mL)",
                null,
                2,
                result,
                String.join("; ", details),
                Boolean.TRUE.equals(result) ? 2 : 0);
    }

    private CriterionScore evaluateNumericLab(
            Map<String, Object> snapshot,
            String field,
            String criterion,
            int weight,
            double threshold,
            String thresholdUnit) {
        LabDatum datum = findLab(snapshot, field).orElse(null);
        Double value = numericValue(datum);
        if (datum == null || value == null) {
            return new CriterionScore(criterion, null, weight, null,
                    "Chưa có dữ liệu.", 0);
        }
        boolean positive = value > threshold;
        return new CriterionScore(
                criterion,
                null,
                weight,
                positive,
                datum.label() + " = " + formatNumber(value) + unitSuffix(datum)
                        + (positive ? " (>" : " (≤") + formatNumber(threshold) + " " + thresholdUnit + ")",
                positive ? weight : 0);
    }

    private CriterionScore evaluateSynovialWbcOrLe(Map<String, Object> snapshot) {
        LabDatum wbc = findLab(snapshot, "synovial_WBC").orElse(null);
        LabDatum le = findLab(snapshot, "synovial_LE").orElse(null);

        Double wbcValue = numericValue(wbc);
        Boolean wbcPositive = wbcValue != null ? wbcValue > 3000.0 : null;
        Boolean lePositive = le != null ? qualitativePositive(le.value(), numericValue(le), 250.0) : null;
        Boolean result = anyPositiveOrNull(wbcPositive, lePositive);

        List<String> details = new ArrayList<>();
        details.add(wbcValue != null
                ? "Synovial WBC = " + formatNumber(wbcValue) + unitSuffix(wbc) + (wbcPositive ? " (>3000)" : " (≤3000)")
                : "Synovial WBC: chưa có dữ liệu");
        details.add(le != null
                ? "Leukocyte Esterase = " + le.value() + (Boolean.TRUE.equals(lePositive) ? " (dương tính)" : " (không dương tính)")
                : "Leukocyte Esterase: chưa có dữ liệu");

        return new CriterionScore(
                "Synovial WBC (>3000 cells/µL) hoặc Leukocyte Esterase (≥++)",
                null,
                3,
                result,
                String.join("; ", details),
                Boolean.TRUE.equals(result) ? 3 : 0);
    }

    private CriterionScore evaluatePositiveAlphaDefensin(Map<String, Object> snapshot) {
        LabDatum datum = findLab(snapshot, "synovial_alpha_defensin").orElse(null);
        if (datum == null) {
            return new CriterionScore("Positive Alpha-Defensin", null, 3, null,
                    "Chưa có dữ liệu Alpha-Defensin.", 0);
        }
        Boolean positive = qualitativePositive(datum.value(), numericValue(datum), 0.12);
        if (positive == null) {
            return new CriterionScore("Positive Alpha-Defensin", null, 3, null,
                    "Alpha-Defensin không đọc được: " + datum.value(), 0);
        }
        return new CriterionScore(
                "Positive Alpha-Defensin",
                null,
                3,
                positive,
                "Alpha-Defensin = " + datum.value() + (positive ? " (dương tính)" : " (âm tính/không vượt ngưỡng)"),
                positive ? 3 : 0);
    }

    private CriterionScore evaluateSinglePositiveCulture(CultureEvidence culture) {
        Boolean result = culture.positiveCount() > 0 && !culture.majorCriteriaMet();
        String detail;
        if (culture.positiveCount() == 0) {
            detail = culture.totalCultureCount() == 0
                    ? "Chưa có dữ liệu nuôi cấy."
                    : "Không có mẫu nuôi cấy dương tính.";
        } else if (culture.majorCriteriaMet()) {
            detail = "Không chấm điểm phụ vì đã thỏa tiêu chí chính với ≥2 mẫu cùng tác nhân.";
            result = false;
        } else {
            detail = culture.positiveCount() == 1
                    ? "Có 1 mẫu nuôi cấy dương tính: " + culture.organismSummary() + "."
                    : "Có nhiều mẫu dương tính nhưng chưa thỏa điều kiện cùng tác nhân: " + culture.organismSummary() + ".";
        }
        return new CriterionScore(
                "1 mẫu nuôi cấy dương tính đơn lẻ",
                null,
                2,
                result,
                detail,
                Boolean.TRUE.equals(result) ? 2 : 0);
    }

    private CriterionScore evaluateHistology(Map<String, Object> snapshot) {
        TextEvidence evidence = textEvidence(snapshot,
                Set.of("histolog", "patholog", "giaiphaubenh", "sinhthiet"),
                Set.of("duongtinh", "positive", "pmnhpf", "pmnhighpower", "tren5pmn"),
                Set.of("amtinh", "negative", "khongthay"));
        if (evidence.result() == null) {
            return new CriterionScore("Giải phẫu bệnh dương tính (>5 PMN per HPF)", null, 3, null,
                    "Chưa có kết quả giải phẫu bệnh mô quanh khớp.", 0);
        }
        return new CriterionScore("Giải phẫu bệnh dương tính (>5 PMN per HPF)", null, 3,
                evidence.result(), evidence.detail(), Boolean.TRUE.equals(evidence.result()) ? 3 : 0);
    }

    private CriterionScore evaluatePurulence(Map<String, Object> snapshot) {
        TextEvidence evidence = textEvidence(snapshot,
                Set.of("comu", "dichmu", "muquanh", "pus", "purulence", "purulent"),
                Set.of("comu", "dichmu", "muquanh", "pus", "purulence", "purulent"),
                Set.of("khongmu", "nomu", "nopurulence", "khongco"));
        if (evidence.result() == null) {
            return new CriterionScore("Mủ trong khớp hoặc quanh khớp giả", null, 3, null,
                    "Chưa có mô tả phẫu thuật khẳng định có/không có mủ.", 0);
        }
        return new CriterionScore("Mủ trong khớp hoặc quanh khớp giả", null, 3,
                evidence.result(), evidence.detail(), Boolean.TRUE.equals(evidence.result()) ? 3 : 0);
    }

    @SuppressWarnings("unchecked")
    private CultureEvidence evaluateCultures(Map<String, Object> snapshot) {
        List<Object> rawItems = getNested(snapshot, "culture_results", "items")
                .filter(List.class::isInstance)
                .map(List.class::cast)
                .orElse(List.of());

        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        Map<String, List<Map<String, Object>>> sensitivitiesByOrganism = new HashMap<>();
        List<String> positiveOrganisms = new ArrayList<>();
        boolean antibioticsBefore = false;
        int positiveCount = 0;

        for (Object raw : rawItems) {
            Map<String, Object> item = asMap(raw);
            if (item == null) {
                continue;
            }
            antibioticsBefore = antibioticsBefore || Boolean.TRUE.equals(asBoolean(item.get("had_antibiotics_before")));
            if (!isPositiveStatus(item.get("result_status"))) {
                continue;
            }
            positiveCount++;
            String organism = firstText(item.get("organism_name"), item.get("name"));
            if (organism == null || organism.isBlank()) {
                organism = "Không ghi rõ tác nhân";
            }
            String key = normalizeToken(organism);
            counts.merge(key, 1, Integer::sum);
            displayNames.putIfAbsent(key, organism);
            positiveOrganisms.add(organism);
            Object sens = item.get("sensitivities");
            if (sens instanceof List<?> list) {
                List<Map<String, Object>> mapped = new ArrayList<>();
                for (Object s : list) {
                    Map<String, Object> sensMap = asMap(s);
                    if (sensMap != null) {
                        mapped.add(sensMap);
                    }
                }
                sensitivitiesByOrganism.putIfAbsent(key, mapped);
            }
        }

        String topKey = counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
        int topCount = topKey != null ? counts.getOrDefault(topKey, 0) : 0;
        String topOrganism = topKey != null ? displayNames.get(topKey) : null;
        List<Map<String, Object>> sensitivities = topKey != null
                ? sensitivitiesByOrganism.getOrDefault(topKey, List.of())
                : List.of();

        boolean major = topCount >= 2;
        String majorDetail;
        if (rawItems.isEmpty()) {
            majorDetail = "Chưa có dữ liệu nuôi cấy.";
        } else if (major) {
            majorDetail = topCount + " mẫu nuôi cấy dương tính cùng tác nhân: " + topOrganism + ".";
        } else if (positiveCount > 0) {
            majorDetail = positiveCount + " mẫu dương tính nhưng chưa có ≥2 mẫu cùng tác nhân (" + String.join(", ", positiveOrganisms) + ").";
        } else {
            majorDetail = "Có dữ liệu nuôi cấy nhưng không có mẫu dương tính.";
        }

        return new CultureEvidence(rawItems.size(), positiveCount, topCount, major, majorDetail,
                topOrganism, positiveOrganisms, sensitivities, antibioticsBefore);
    }

    private Optional<LabDatum> findLab(Map<String, Object> snapshot, String field) {
        LabAlias alias = LAB_ALIASES.get(field);
        if (alias == null) {
            return Optional.empty();
        }
        List<LabDatum> datums = collectLabDatums(snapshot);
        return datums.stream()
                .filter(d -> alias.matches(d))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private List<LabDatum> collectLabDatums(Map<String, Object> snapshot) {
        Object latestObj = getNested(snapshot, "lab_results", "latest")
                .orElseGet(() -> getNested(snapshot, "lab_results").orElse(null));
        Map<String, Object> latest = asMap(latestObj);
        if (latest == null) {
            return List.of();
        }
        List<LabDatum> datums = new ArrayList<>();
        for (Map.Entry<String, Object> entry : latest.entrySet()) {
            collectLabDatums(entry.getKey(), entry.getKey(), entry.getValue(), datums);
        }
        return datums;
    }

    private void collectLabDatums(String section, String label, Object node, List<LabDatum> out) {
        if (node instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> row = asMap(raw);
                if (row == null) {
                    continue;
                }
                Object value = firstNonNull(row.get("value"), row.get("result"));
                if (!isFilled(value)) {
                    continue;
                }
                String id = text(row.get("id"));
                String name = text(row.get("name"));
                String unit = text(row.get("unit"));
                out.add(new LabDatum(section, id, name != null ? name : label, value, unit));
            }
            return;
        }

        Map<String, Object> map = asMap(node);
        if (map != null) {
            if (map.containsKey("value") || map.containsKey("result")) {
                Object value = firstNonNull(map.get("value"), map.get("result"));
                if (isFilled(value)) {
                    out.add(new LabDatum(section, label, label, value, text(map.get("unit"))));
                }
                return;
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                collectLabDatums(section, entry.getKey(), entry.getValue(), out);
            }
            return;
        }

        if (isFilled(node)) {
            out.add(new LabDatum(section, label, label, node, null));
        }
    }

    private Map<String, Object> supportingEvidence(Map<String, Object> snapshot) {
        Map<String, Object> supporting = new LinkedHashMap<>();
        findLab(snapshot, "serum_IL6").ifPresent(il6 -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", "Serum IL-6");
            item.put("value", il6.value());
            item.put("unit", il6.unit());
            item.put("note", "Dữ liệu hỗ trợ viêm/nhiễm trùng; không dùng làm tiêu chí quyết định trong điểm core ở phiên bản này.");
            supporting.put("serum_il6", item);
        });
        return supporting;
    }

    private List<Map<String, Object>> buildWarnings(
            Map<String, Object> snapshot,
            CultureEvidence culture,
            List<CriterionScore> scores) {
        List<Map<String, Object>> warnings = new ArrayList<>();

        Optional<Object> allergy = getNested(snapshot, "medical_history", "allergies", "is_allergy");
        if (allergy.map(this::asBoolean).orElse(false)) {
            String note = getNested(snapshot, "medical_history", "allergies", "allergy_note")
                    .map(Object::toString)
                    .orElse("Có tiền sử dị ứng thuốc.");
            warnings.add(warning("ALLERGY_ALERT", "HIGH", note));
        }

        if (culture.antibioticsBefore()) {
            warnings.add(warning("DATA_QUALITY", "MEDIUM",
                    "Có mẫu nuôi cấy được ghi nhận sau khi đã dùng kháng sinh; kết quả âm tính cần diễn giải thận trọng."));
        }

        long missingCritical = scores.stream()
                .filter(s -> s.result() == null)
                .filter(s -> s.scoreWeight() >= 2)
                .count();
        if (missingCritical > 0) {
            warnings.add(warning("DATA_COMPLETENESS", "MEDIUM",
                    "Còn thiếu " + missingCritical + " tiêu chí chẩn đoán quan trọng; hệ thống không suy đoán các tiêu chí này."));
        }

        return warnings;
    }

    private Map<String, Object> warning(String type, String severity, String message) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("type", type);
        warning.put("severity", severity);
        warning.put("message", message);
        return warning;
    }

    private String interpret(boolean majorCriteriaMet, int totalMinorScore) {
        if (majorCriteriaMet || totalMinorScore >= INFECTED_SCORE_THRESHOLD) {
            return "INFECTED";
        }
        if (totalMinorScore >= INCONCLUSIVE_SCORE_MIN) {
            return "INCONCLUSIVE";
        }
        return "NOT_INFECTED";
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

    private String majorConclusion(boolean majorCriteriaMet, MajorCriterion sinus, CultureEvidence culture) {
        if (!majorCriteriaMet) {
            return "Chưa thỏa tiêu chí chính; diễn giải dựa trên tổng điểm minor và dữ liệu còn thiếu.";
        }
        List<String> reasons = new ArrayList<>();
        if (culture.majorCriteriaMet()) {
            reasons.add("≥2 mẫu nuôi cấy cùng tác nhân");
        }
        if (sinus.result == Boolean.TRUE) {
            reasons.add("đường rò thông với khớp giả");
        }
        return "Đã thỏa tiêu chí chính (" + String.join("; ", reasons) + ") → kết luận INFECTED.";
    }

    private String minorScoreNote(int totalMinorScore, boolean majorCriteriaMet) {
        String base;
        if (totalMinorScore >= INFECTED_SCORE_THRESHOLD) {
            base = totalMinorScore + "/20 điểm minor khả dụng → ≥6, phân loại INFECTED.";
        } else if (totalMinorScore >= INCONCLUSIVE_SCORE_MIN) {
            base = totalMinorScore + "/20 điểm minor khả dụng → 4-5, phân loại INCONCLUSIVE.";
        } else {
            base = totalMinorScore + "/20 điểm minor khả dụng → ≤3, phân loại NOT_INFECTED nếu không có tiêu chí chính.";
        }
        return majorCriteriaMet ? base + " Tuy nhiên tiêu chí chính đã đủ để kết luận INFECTED." : base;
    }

    private String primaryDiagnosis(Map<String, Object> snapshot, String interpretation) {
        String joint = getNested(snapshot, "clinical_records", "infection_assessment", "prosthesis_joint")
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .map(s -> " " + s.replace('_', ' '))
                .orElse("");
        return switch (interpretation) {
            case "INFECTED" -> "Nhiễm trùng khớp giả" + joint;
            case "INCONCLUSIVE" -> "Chưa xác định nhiễm trùng khớp giả" + joint;
            default -> "Chưa đủ bằng chứng nhiễm trùng khớp giả" + joint;
        };
    }

    private String infectionClassification(Map<String, Object> snapshot) {
        Optional<Object> onsetTiming = getNested(
                snapshot, "clinical_records", "infection_assessment", "onset_timing");
        return onsetTiming
                .or(() -> getNested(
                        snapshot, "clinical_records", "infection_assessment", "suspected_infection_type"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .orElse("UNKNOWN");
    }

    private String infectionClassificationReasoning(Map<String, Object> snapshot) {
        String type = infectionClassification(snapshot);
        Optional<Object> transmissionRoute = getNested(
                snapshot, "clinical_records", "infection_assessment", "suspected_transmission_route");
        Optional<Object> hematogenous = getNested(snapshot, "clinical_records", "infection_assessment", "hematogenous_suspected");
        Optional<Object> stability = getNested(snapshot, "clinical_records", "infection_assessment", "implant_stability");
        List<String> facts = new ArrayList<>();
        facts.add("Thời điểm khởi phát so với phẫu thuật gần nhất: " + type);
        transmissionRoute.ifPresent(v -> facts.add("đường lây nhiễm nghi ngờ: " + v));
        hematogenous.ifPresent(v -> facts.add("nghi đường máu: " + v));
        stability.ifPresent(v -> facts.add("ổn định implant: " + v));
        return String.join("; ", facts) + ".";
    }

    private Map<String, Object> identifiedOrganism(CultureEvidence culture) {
        Map<String, Object> organism = new LinkedHashMap<>();
        if (culture.topOrganism() == null || culture.topOrganism().isBlank()) {
            organism.put("name", "Chưa xác định");
            organism.put("resistance_profile", "UNKNOWN");
            organism.put("resistance_detail", "Chưa có mẫu nuôi cấy dương tính hoặc chưa định danh tác nhân.");
            organism.put("biofilm_forming", false);
            organism.put("virulence_note", "Không suy đoán tác nhân khi thiếu bằng chứng vi sinh.");
            return organism;
        }

        String profile = resistanceProfile(culture.topOrganism(), culture.sensitivities());
        organism.put("name", culture.topOrganism());
        organism.put("resistance_profile", profile);
        organism.put("resistance_detail", resistanceDetail(culture.sensitivities()));
        organism.put("biofilm_forming", true);
        organism.put("virulence_note", virulenceNote(culture.topOrganism()));
        return organism;
    }

    private String reasoningSummary(String interpretation, boolean majorCriteriaMet, int score, CultureEvidence culture) {
        List<String> parts = new ArrayList<>();
        if (majorCriteriaMet) {
            parts.add("Kết luận INFECTED theo tiêu chí chính.");
        } else {
            parts.add("Không thỏa tiêu chí chính; phân loại theo điểm minor = " + score + ".");
        }
        parts.add("Ngưỡng diễn giải: ≥6 INFECTED, 4-5 INCONCLUSIVE, ≤3 NOT_INFECTED.");
        if (culture.topOrganism() != null) {
            parts.add("Tác nhân nổi bật: " + culture.topOrganism() + ".");
        }
        parts.add("Kết luận hiện tại: " + interpretation + ".");
        return String.join(" ", parts);
    }

    private String resistanceProfile(String organism, List<Map<String, Object>> sensitivities) {
        String normOrganism = normalizeToken(organism);
        boolean staphAureus = normOrganism.contains("staphylococcusaureus")
                || normOrganism.contains("staphaureus")
                || normOrganism.contains("saureus");
        if (!staphAureus) {
            return "Theo kháng sinh đồ";
        }
        Optional<String> oxacillin = sensitivityCode(sensitivities, Set.of("oxacillin", "methicillin", "cefoxitin"));
        if (oxacillin.map(code -> code.equals("R")).orElse(false)) {
            return "MRSA";
        }
        if (oxacillin.map(code -> code.equals("S")).orElse(false)) {
            return "MSSA";
        }
        return "Staphylococcus aureus - chưa rõ methicillin";
    }

    private String resistanceDetail(List<Map<String, Object>> sensitivities) {
        if (sensitivities == null || sensitivities.isEmpty()) {
            return "Chưa có kháng sinh đồ.";
        }
        List<String> items = new ArrayList<>();
        for (Map<String, Object> sens : sensitivities) {
            String name = firstText(sens.get("antibiotic_name"), sens.get("antibioticName"));
            String code = firstText(sens.get("sensitivity_code"), sens.get("sensitivityCode"));
            String mic = firstText(sens.get("mic_value"), sens.get("micValue"));
            if (name == null || code == null) {
                continue;
            }
            items.add(name + " " + code + (mic != null && !mic.isBlank() ? " (MIC " + mic + ")" : ""));
        }
        return items.isEmpty() ? "Chưa có kháng sinh đồ đọc được." : String.join("; ", items) + ".";
    }

    private Optional<String> sensitivityCode(List<Map<String, Object>> sensitivities, Set<String> antibioticAliases) {
        if (sensitivities == null) {
            return Optional.empty();
        }
        for (Map<String, Object> sens : sensitivities) {
            String name = normalizeToken(firstText(sens.get("antibiotic_name"), sens.get("antibioticName")));
            if (antibioticAliases.stream().map(PjiDiagnosticRuleEngine::normalizeToken).anyMatch(name::contains)) {
                String code = firstText(sens.get("sensitivity_code"), sens.get("sensitivityCode"));
                if (code != null) {
                    return Optional.of(code.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return Optional.empty();
    }

    private String virulenceNote(String organism) {
        String norm = normalizeToken(organism);
        if (norm.contains("staphylococcus")) {
            return "Staphylococcus spp. là tác nhân PJI thường gặp và có khả năng tạo biofilm trên implant.";
        }
        return "Tác nhân được định danh từ mẫu dương tính; cần đối chiếu bối cảnh lâm sàng và nguy cơ nhiễm bẩn mẫu.";
    }

    private TextEvidence textEvidence(
            Map<String, Object> snapshot,
            Set<String> contextTokens,
            Set<String> positiveTokens,
            Set<String> negativeTokens) {
        List<String> texts = collectClinicalTexts(snapshot);
        for (String raw : texts) {
            String norm = normalizeToken(raw);
            boolean inContext = contextTokens.stream().anyMatch(norm::contains);
            if (!inContext) {
                continue;
            }
            boolean negative = negativeTokens.stream().anyMatch(norm::contains);
            boolean positive = positiveTokens.stream().anyMatch(norm::contains) || norm.matches(".*[>≥]5.*pmn.*");
            if (negative) {
                return new TextEvidence(false, "Mô tả ghi nhận âm tính/không có: " + raw);
            }
            if (positive) {
                return new TextEvidence(true, "Mô tả ghi nhận dương tính: " + raw);
            }
        }
        return new TextEvidence(null, "");
    }

    @SuppressWarnings("unchecked")
    private List<String> collectClinicalTexts(Map<String, Object> snapshot) {
        List<String> texts = new ArrayList<>();
        getNested(snapshot, "clinical_records", "infection_assessment", "soft_tissue")
                .map(Object::toString).ifPresent(texts::add);
        getNested(snapshot, "clinical_records", "notations")
                .map(Object::toString).ifPresent(texts::add);

        Object surgeries = getNested(snapshot, "surgeries", "items").orElse(null);
        if (surgeries instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> item = asMap(raw);
                if (item != null) {
                    String findings = firstText(item.get("findings"));
                    if (findings != null) {
                        texts.add(findings);
                    }
                }
            }
        }

        Object cultures = getNested(snapshot, "culture_results", "items").orElse(null);
        if (cultures instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> item = asMap(raw);
                if (item != null) {
                    String sample = firstText(item.get("sample_type"), item.get("notes"));
                    if (sample != null) {
                        texts.add(sample);
                    }
                    String notes = firstText(item.get("notes"));
                    if (notes != null) {
                        texts.add(notes);
                    }
                }
            }
        }

        return texts;
    }

    private int missingCount(List<CriterionScore> scores) {
        return (int) scores.stream().filter(s -> s.result() == null).count();
    }

    private Map<String, Object> majorCriterion(String criterion, boolean result, String detail, boolean decisive) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("criterion", criterion);
        item.put("result", result);
        item.put("result_detail", detail);
        item.put("is_decisive", decisive);
        return item;
    }

    private Boolean anyPositiveOrNull(Boolean... values) {
        boolean sawData = false;
        for (Boolean value : values) {
            if (value == Boolean.TRUE) {
                return true;
            }
            if (value != null) {
                sawData = true;
            }
        }
        return sawData ? false : null;
    }

    private Boolean qualitativePositive(Object rawValue, Double numeric, double numericThreshold) {
        if (rawValue == null) {
            return null;
        }
        String raw = rawValue.toString().trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String norm = normalizeToken(raw);
        if (raw.equals("-") || raw.equals("+")
                || norm.contains("negative") || norm.contains("amtinh")
                || norm.contains("notdetected")) {
            return false;
        }
        if (raw.contains("++") || raw.contains("+++")
                || norm.contains("positive") || norm.contains("duongtinh")
                || norm.contains("detected")) {
            return true;
        }
        if (lower.equals("true") || lower.equals("yes")) {
            return true;
        }
        if (lower.equals("false") || lower.equals("no")) {
            return false;
        }
        if (numeric != null) {
            return numeric > numericThreshold;
        }
        return null;
    }

    private Double numericValue(LabDatum datum) {
        return datum != null ? numericValue(datum.value()) : null;
    }

    private Double numericValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        Matcher matcher = NUMBER_PATTERN.matcher(raw.toString());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double dDimerNgMl(LabDatum datum) {
        Double value = numericValue(datum);
        if (value == null) {
            return null;
        }
        String unit = normalizeToken(datum != null ? datum.unit() : null);
        if (unit.contains("ngml") || unit.contains("ngperml")) {
            return value;
        }
        if (unit.contains("mgl") || value <= 20.0) {
            return value * 1000.0;
        }
        return value;
    }

    private boolean isPositiveStatus(Object status) {
        String norm = normalizeToken(status);
        return norm.equals("positive") || norm.equals("duongtinh") || norm.equals("pos");
    }

    private boolean isFilled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String str) {
            return !str.isBlank();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(this::isFilled);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return null;
    }

    private Optional<Object> getNested(Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            Map<String, Object> map = asMap(current);
            if (map == null || !map.containsKey(key)) {
                return Optional.empty();
            }
            current = map.get(key);
        }
        return Optional.ofNullable(current);
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String norm = normalizeToken(value);
        if (Set.of("true", "yes", "y", "1", "co", "duongtinh", "positive").contains(norm)) {
            return true;
        }
        if (Set.of("false", "no", "n", "0", "khong", "amtinh", "negative").contains(norm)) {
            return false;
        }
        return null;
    }

    private Optional<Long> daysSince(String dateText) {
        try {
            LocalDate onset = LocalDate.parse(dateText);
            return Optional.of(ChronoUnit.DAYS.between(onset, LocalDate.now()));
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate onset = OffsetDateTime.parse(dateText).toLocalDate();
                return Optional.of(ChronoUnit.DAYS.between(onset, LocalDate.now()));
            } catch (DateTimeParseException ignoredAgain) {
                return Optional.empty();
            }
        }
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value != null ? value.toString() : null;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String unitSuffix(LabDatum datum) {
        if (datum == null || datum.unit() == null || datum.unit().isBlank()) {
            return "";
        }
        return " " + datum.unit();
    }

    private String formatNumber(double value) {
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String normalizeToken(Object value) {
        if (value == null) {
            return "";
        }
        String s = Normalizer.normalize(value.toString(), Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT)
                .replace("đ", "d");
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public record DiagnosticResult(
            String title,
            Map<String, Object> itemJson,
            Map<String, Object> assessmentJson,
            Map<String, Object> explanationJson,
            List<Map<String, Object>> warningsJson) {
    }

    private record LabAlias(Set<String> ids, Set<String> names, Set<String> sections) {
        boolean matches(LabDatum datum) {
            String id = normalizeToken(datum.id());
            String name = normalizeToken(datum.name());
            String section = datum.section();
            boolean sectionMatches = sections.contains(section)
                    || (sections.contains("latest") && !Set.of(
                            "hematology_tests",
                            "fluid_analysis",
                            "biochemical_data").contains(section));
            if (!sectionMatches) {
                return false;
            }
            if (ids.contains(id)) {
                return true;
            }
            return names.stream().anyMatch(alias -> name.contains(alias) || id.contains(alias));
        }
    }

    private record LabDatum(String section, String id, String name, Object value, String unit) {
        String label() {
            return name != null && !name.isBlank() ? name : id;
        }
    }

    private record MajorCriterion(Boolean result, String detail) {
    }

    private record CultureEvidence(
            int totalCultureCount,
            int positiveCount,
            int topOrganismPositiveCount,
            boolean majorCriteriaMet,
            String majorDetail,
            String topOrganism,
            List<String> positiveOrganisms,
            List<Map<String, Object>> sensitivities,
            boolean antibioticsBefore) {
        String organismSummary() {
            if (positiveOrganisms == null || positiveOrganisms.isEmpty()) {
                return "không có";
            }
            return String.join(", ", new LinkedHashSet<>(positiveOrganisms));
        }
    }

    private record CriterionScore(
            String criterion,
            String criterionVi,
            int scoreWeight,
            Boolean result,
            String resultDetail,
            int scoreAwarded) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("criterion", criterion);
            if (criterionVi != null) {
                map.put("criterion_vi", criterionVi);
            }
            map.put("score_weight", scoreWeight);
            map.put("result", result);
            map.put("result_detail", resultDetail);
            map.put("score_awarded", scoreAwarded);
            return map;
        }
    }

    private record TextEvidence(Boolean result, String detail) {
    }
}
