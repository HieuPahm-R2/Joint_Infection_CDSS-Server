package com.vietnam.pji.services.diagnosis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Applies the deterministic PJI major and minor clinical criteria. */
@Component
class PjiDiagnosticCriteriaEvaluator {

    private final PjiDiagnosticSnapshotReader snapshotReader;

    PjiDiagnosticCriteriaEvaluator(PjiDiagnosticSnapshotReader snapshotReader) {
        this.snapshotReader = snapshotReader;
    }

    MajorCriterion evaluateSinusTract(Map<String, Object> snapshot) {
        Optional<Object> value = snapshotReader.getNested(snapshot, "clinical_records", "symptoms", "sinus_tract");
        if (value.isEmpty()) {
            return new MajorCriterion(null, "Chưa có dữ liệu đánh giá đường rò.");
        }
        Boolean verdict = snapshotReader.asBoolean(value.get());
        if (verdict == null) {
            return new MajorCriterion(null, "Giá trị đường rò không đọc được: " + value.get());
        }
        return new MajorCriterion(verdict, verdict ? "Ghi nhận có đường rò thông với khớp giả."
                : "Không ghi nhận đường rò thông với khớp giả.");
    }

    List<CriterionScore> evaluateMinorCriteria(Map<String, Object> snapshot,
            PjiCultureEvidenceEvaluator.CultureEvidence culture) {
        List<CriterionScore> scores = new ArrayList<>();
        scores.add(evaluateSerumCrpOrDimer(snapshot));
        scores.add(evaluateNumericLab(snapshot, "serum_ESR", "Tốc độ máu lắng tăng ESR (>30 mm/h)", 1, 30.0, "mm/h"));
        scores.add(evaluateSynovialWbcOrLe(snapshot));
        scores.add(evaluateNumericLab(snapshot, "synovial_PMN", "Synovial PMN% (>80%)", 2, 80.0, "%"));
        scores.add(evaluatePositiveAlphaDefensin(snapshot));
        scores.add(evaluateNumericLab(snapshot, "synovial_CRP", "Synovial CRP (>6.9 mg/L)", 1, 6.9, "mg/L"));
        scores.add(evaluateSinglePositiveCulture(culture));
        scores.add(evaluateHistology(snapshot));
        scores.add(evaluatePurulence(snapshot));
        return scores;
    }

    private CriterionScore evaluateSerumCrpOrDimer(Map<String, Object> snapshot) {
        PjiDiagnosticSnapshotReader.LabDatum crp = snapshotReader.findLab(snapshot, "serum_CRP").orElse(null);
        PjiDiagnosticSnapshotReader.LabDatum dDimer = snapshotReader.findLab(snapshot, "serum_D_Dimer").orElse(null);
        Double crpValue = snapshotReader.numericValue(crp);
        Double dDimerNgMl = snapshotReader.dDimerNgMl(dDimer);
        Boolean crpPositive = crpValue != null ? crpValue > 10.0 : null;
        Boolean dDimerPositive = dDimerNgMl != null ? dDimerNgMl > 860.0 : null;
        List<String> details = new ArrayList<>();
        details.add(crpValue != null ? "CRP = " + snapshotReader.formatNumber(crpValue) + snapshotReader.unitSuffix(crp)
                + (crpPositive ? " (>10)" : " (≤10)") : "CRP huyết thanh: chưa có dữ liệu");
        details.add(dDimerNgMl != null ? "D-Dimer = " + snapshotReader.formatNumber(dDimerNgMl) + " ng/mL FEU"
                + (dDimerPositive ? " (>860)" : " (≤860)") : "D-Dimer: chưa có dữ liệu");
        Boolean result = snapshotReader.anyPositiveOrNull(crpPositive, dDimerPositive);
        return new CriterionScore("Serum CRP (>10 mg/L) hoặc D-Dimer (>860 ng/mL)", null, 2, result,
                String.join("; ", details), Boolean.TRUE.equals(result) ? 2 : 0);
    }

    private CriterionScore evaluateNumericLab(Map<String, Object> snapshot, String field, String criterion,
            int weight, double threshold, String thresholdUnit) {
        PjiDiagnosticSnapshotReader.LabDatum datum = snapshotReader.findLab(snapshot, field).orElse(null);
        Double value = snapshotReader.numericValue(datum);
        if (datum == null || value == null) {
            return new CriterionScore(criterion, null, weight, null, "Chưa có dữ liệu.", 0);
        }
        boolean positive = value > threshold;
        return new CriterionScore(criterion, null, weight, positive,
                datum.label() + " = " + snapshotReader.formatNumber(value) + snapshotReader.unitSuffix(datum)
                        + (positive ? " (>" : " (≤") + snapshotReader.formatNumber(threshold) + " " + thresholdUnit + ")",
                positive ? weight : 0);
    }

    private CriterionScore evaluateSynovialWbcOrLe(Map<String, Object> snapshot) {
        PjiDiagnosticSnapshotReader.LabDatum wbc = snapshotReader.findLab(snapshot, "synovial_WBC").orElse(null);
        PjiDiagnosticSnapshotReader.LabDatum le = snapshotReader.findLab(snapshot, "synovial_LE").orElse(null);
        Double wbcValue = snapshotReader.numericValue(wbc);
        Boolean wbcPositive = wbcValue != null ? wbcValue > 3000.0 : null;
        Boolean lePositive = le != null ? snapshotReader.qualitativePositive(le.value(), snapshotReader.numericValue(le), 250.0) : null;
        List<String> details = new ArrayList<>();
        details.add(wbcValue != null ? "Synovial WBC = " + snapshotReader.formatNumber(wbcValue)
                + snapshotReader.unitSuffix(wbc) + (wbcPositive ? " (>3000)" : " (≤3000)")
                : "Synovial WBC: chưa có dữ liệu");
        details.add(le != null ? "Leukocyte Esterase = " + le.value()
                + (Boolean.TRUE.equals(lePositive) ? " (dương tính)" : " (không dương tính)")
                : "Leukocyte Esterase: chưa có dữ liệu");
        Boolean result = snapshotReader.anyPositiveOrNull(wbcPositive, lePositive);
        return new CriterionScore("Synovial WBC (>3000 cells/µL) hoặc Leukocyte Esterase (≥++)", null, 3,
                result, String.join("; ", details), Boolean.TRUE.equals(result) ? 3 : 0);
    }

    private CriterionScore evaluatePositiveAlphaDefensin(Map<String, Object> snapshot) {
        PjiDiagnosticSnapshotReader.LabDatum datum = snapshotReader.findLab(snapshot, "synovial_alpha_defensin").orElse(null);
        if (datum == null) {
            return new CriterionScore("Positive Alpha-Defensin", null, 3, null, "Chưa có dữ liệu Alpha-Defensin.", 0);
        }
        Boolean positive = snapshotReader.qualitativePositive(datum.value(), snapshotReader.numericValue(datum), 0.12);
        if (positive == null) {
            return new CriterionScore("Positive Alpha-Defensin", null, 3, null,
                    "Alpha-Defensin không đọc được: " + datum.value(), 0);
        }
        return new CriterionScore("Positive Alpha-Defensin", null, 3, positive,
                "Alpha-Defensin = " + datum.value() + (positive ? " (dương tính)" : " (âm tính/không vượt ngưỡng)"),
                positive ? 3 : 0);
    }

    private CriterionScore evaluateSinglePositiveCulture(PjiCultureEvidenceEvaluator.CultureEvidence culture) {
        Boolean result = culture.positiveCount() > 0 && !culture.majorCriteriaMet();
        String detail;
        if (culture.positiveCount() == 0) {
            detail = culture.totalCultureCount() == 0 ? "Chưa có dữ liệu nuôi cấy." : "Không có mẫu nuôi cấy dương tính.";
        } else if (culture.majorCriteriaMet()) {
            detail = "Không chấm điểm phụ vì đã thỏa tiêu chí chính với ≥2 mẫu cùng tác nhân.";
            result = false;
        } else {
            detail = culture.positiveCount() == 1 ? "Có 1 mẫu nuôi cấy dương tính: " + culture.organismSummary() + "."
                    : "Có nhiều mẫu dương tính nhưng chưa thỏa điều kiện cùng tác nhân: " + culture.organismSummary() + ".";
        }
        return new CriterionScore("1 mẫu nuôi cấy dương tính đơn lẻ", null, 2, result, detail,
                Boolean.TRUE.equals(result) ? 2 : 0);
    }

    private CriterionScore evaluateHistology(Map<String, Object> snapshot) {
        PjiDiagnosticSnapshotReader.TextEvidence evidence = snapshotReader.textEvidence(snapshot,
                Set.of("histolog", "patholog", "giaiphaubenh", "sinhthiet"),
                Set.of("duongtinh", "positive", "pmnhpf", "pmnhighpower", "tren5pmn"),
                Set.of("amtinh", "negative", "khongthay"));
        return evidence.result() == null
                ? new CriterionScore("Giải phẫu bệnh dương tính (>5 PMN per HPF)", null, 3, null,
                        "Chưa có kết quả giải phẫu bệnh mô quanh khớp.", 0)
                : new CriterionScore("Giải phẫu bệnh dương tính (>5 PMN per HPF)", null, 3, evidence.result(),
                        evidence.detail(), Boolean.TRUE.equals(evidence.result()) ? 3 : 0);
    }

    private CriterionScore evaluatePurulence(Map<String, Object> snapshot) {
        PjiDiagnosticSnapshotReader.TextEvidence evidence = snapshotReader.textEvidence(snapshot,
                Set.of("comu", "dichmu", "muquanh", "pus", "purulence", "purulent"),
                Set.of("comu", "dichmu", "muquanh", "pus", "purulence", "purulent"),
                Set.of("khongmu", "nomu", "nopurulence", "khongco"));
        return evidence.result() == null
                ? new CriterionScore("Mủ trong khớp hoặc quanh khớp giả", null, 3, null,
                        "Chưa có mô tả phẫu thuật khẳng định có/không có mủ.", 0)
                : new CriterionScore("Mủ trong khớp hoặc quanh khớp giả", null, 3, evidence.result(), evidence.detail(),
                        Boolean.TRUE.equals(evidence.result()) ? 3 : 0);
    }

    record MajorCriterion(Boolean result, String detail) {
    }

    record CriterionScore(String criterion, String criterionVi, int scoreWeight, Boolean result,
            String resultDetail, int scoreAwarded) {
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
}
