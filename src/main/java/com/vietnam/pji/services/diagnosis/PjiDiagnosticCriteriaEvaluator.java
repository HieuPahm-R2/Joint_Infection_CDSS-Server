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
        ClinicalPhase phase = clinicalPhase(snapshot);
        double wbcThreshold = phase == ClinicalPhase.ACUTE ? 10_000.0 : 3_000.0;
        double pmnThreshold = phase == ClinicalPhase.ACUTE ? 90.0 : 70.0;
        List<CriterionScore> scores = new ArrayList<>();
        scores.add(evaluateSerumCrpOrDimer(snapshot, phase));
        scores.add(phase == ClinicalPhase.ACUTE
                ? new CriterionScore("Tốc độ máu lắng ESR (không áp dụng cho ca cấp)", null, 1, false,
                        "ICM không sử dụng ESR để chấm điểm ca cấp.", 0)
                : evaluateNumericLab(snapshot, "serum_ESR", "Tốc độ máu lắng tăng ESR (>30 mm/h)", 1, 30.0, "mm/h"));
        scores.add(evaluateSynovialWbcLeOrAlphaDefensin(snapshot, wbcThreshold));
        scores.add(evaluateNumericLab(snapshot, "synovial_PMN",
                "Synovial PMN% (>" + snapshotReader.formatNumber(pmnThreshold) + "% - " + phase.label() + ")",
                2, pmnThreshold, "%"));
        scores.add(evaluateSinglePositiveCulture(culture));
        scores.add(evaluateHistology(snapshot));
        scores.add(evaluatePurulence(snapshot));
        return scores;
    }

    private CriterionScore evaluateSerumCrpOrDimer(Map<String, Object> snapshot, ClinicalPhase phase) {
        PjiDiagnosticSnapshotReader.LabDatum crp = snapshotReader.findLab(snapshot, "serum_CRP").orElse(null);
        PjiDiagnosticSnapshotReader.LabDatum dDimer = snapshotReader.findLab(snapshot, "serum_D_Dimer").orElse(null);
        Double crpValue = snapshotReader.numericValue(crp);
        Double dDimerNgMl = snapshotReader.dDimerNgMl(dDimer);
        double crpThreshold = phase == ClinicalPhase.ACUTE ? 100.0 : 10.0;
        Boolean crpPositive = crpValue != null ? crpValue > crpThreshold : null;
        Boolean dDimerPositive = phase == ClinicalPhase.CHRONIC && dDimerNgMl != null
                ? dDimerNgMl > 860.0 : null;
        List<String> details = new ArrayList<>();
        details.add(crpValue != null ? "CRP = " + snapshotReader.formatNumber(crpValue) + snapshotReader.unitSuffix(crp)
                + (crpPositive ? " (>" : " (≤") + snapshotReader.formatNumber(crpThreshold) + ")"
                : "CRP huyết thanh: chưa có dữ liệu");
        details.add(phase == ClinicalPhase.ACUTE ? "D-Dimer: chưa có ngưỡng ICM cho ca cấp"
                : dDimerNgMl != null ? "D-Dimer = " + snapshotReader.formatNumber(dDimerNgMl) + " ng/mL FEU"
                        + (dDimerPositive ? " (>860)" : " (≤860)")
                        : "D-Dimer: chưa có dữ liệu");
        Boolean result = snapshotReader.anyPositiveOrNull(crpPositive, dDimerPositive);
        return new CriterionScore("Serum CRP (>" + snapshotReader.formatNumber(crpThreshold)
                + " mg/L)" + (phase == ClinicalPhase.CHRONIC ? " hoặc D-Dimer (>860 ng/mL)" : ""), null, 2, result,
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

    private CriterionScore evaluateSynovialWbcLeOrAlphaDefensin(Map<String, Object> snapshot, double wbcThreshold) {
        PjiDiagnosticSnapshotReader.LabDatum wbc = snapshotReader.findLab(snapshot, "synovial_WBC").orElse(null);
        PjiDiagnosticSnapshotReader.LabDatum le = snapshotReader.findLab(snapshot, "synovial_LE").orElse(null);
        PjiDiagnosticSnapshotReader.LabDatum alpha = snapshotReader.findLab(snapshot, "synovial_alpha_defensin")
                .orElse(null);
        Double wbcValue = snapshotReader.numericValue(wbc);
        Boolean wbcPositive = wbcValue != null ? wbcValue > wbcThreshold : null;
        Boolean lePositive = le != null
                ? snapshotReader.qualitativePositive(le.value(), null, Double.POSITIVE_INFINITY)
                : null;
        Double alphaValue = snapshotReader.numericValue(alpha);
        Boolean alphaPositive = alpha == null ? null
                : alphaValue != null ? alphaValue >= 1.0
                : snapshotReader.qualitativePositive(alpha.value(), null, Double.POSITIVE_INFINITY);
        List<String> details = new ArrayList<>();
        details.add(wbcValue != null ? "Synovial WBC = " + snapshotReader.formatNumber(wbcValue)
                + snapshotReader.unitSuffix(wbc) + (wbcPositive ? " (>" : " (≤")
                + snapshotReader.formatNumber(wbcThreshold) + ")"
                : "Synovial WBC: chưa có dữ liệu");
        details.add(le != null ? "Leukocyte Esterase = " + le.value()
                + (Boolean.TRUE.equals(lePositive) ? " (dương tính)" : " (không dương tính)")
                : "Leukocyte Esterase: chưa có dữ liệu");
        details.add(alpha != null ? "Alpha-Defensin = " + alpha.value()
                + (Boolean.TRUE.equals(alphaPositive) ? " (dương tính, cutoff ≥1.0)" : " (âm tính/không đạt cutoff 1.0)")
                : "Alpha-Defensin: chưa có dữ liệu");
        Boolean result = snapshotReader.anyPositiveOrNull(wbcPositive, lePositive, alphaPositive);
        return new CriterionScore("Synovial WBC (>" + snapshotReader.formatNumber(wbcThreshold)
                + " cells/µL), Leukocyte Esterase (≥++) hoặc Alpha-Defensin (signal/cutoff ≥1.0)", null, 3,
                result, String.join("; ", details), Boolean.TRUE.equals(result) ? 3 : 0);
    }

    private ClinicalPhase clinicalPhase(Map<String, Object> snapshot) {
        Object raw = snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "onset_timing")
                .or(() -> snapshotReader.getNested(snapshot, "clinical_records", "infection_assessment", "suspected_infection_type"))
                .or(() -> snapshotReader.getNested(snapshot, "clinical_records", "onset_timing"))
                .orElse(null);
        String normalized = PjiDiagnosticSnapshotReader.normalizeToken(raw);
        return Set.of("early", "acute", "acutepostoperative", "acutehematogenous", "earlypostoperative")
                .contains(normalized) ? ClinicalPhase.ACUTE : ClinicalPhase.CHRONIC;
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
        Boolean structured = structuredSurgeryEvidence(snapshot, "positive_histology");
        if (structured != null) {
            return new CriterionScore("Giải phẫu bệnh dương tính (>5 PMN per HPF)", null, 3, structured,
                    structured ? "Kết quả mô học có cấu trúc: dương tính."
                            : "Kết quả mô học có cấu trúc: âm tính.",
                    structured ? 3 : 0);
        }
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
        Boolean structured = structuredSurgeryEvidence(snapshot, "intraoperative_purulence");
        if (structured != null) {
            return new CriterionScore("Mủ trong khớp hoặc quanh khớp giả", null, 3, structured,
                    structured ? "Dữ liệu trong mổ có cấu trúc: ghi nhận có mủ."
                            : "Dữ liệu trong mổ có cấu trúc: không ghi nhận mủ.",
                    structured ? 3 : 0);
        }
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

    private Boolean structuredSurgeryEvidence(Map<String, Object> snapshot, String field) {
        Object rawItems = snapshotReader.getNested(snapshot, "surgeries", "items").orElse(null);
        if (!(rawItems instanceof List<?> items)) {
            return null;
        }
        boolean sawNegative = false;
        for (Object raw : items) {
            Map<String, Object> surgery = snapshotReader.asMap(raw);
            if (surgery == null || !surgery.containsKey(field)) {
                continue;
            }
            Boolean value = snapshotReader.asBoolean(surgery.get(field));
            if (value == Boolean.TRUE) {
                return true;
            }
            if (value == Boolean.FALSE) {
                sawNegative = true;
            }
        }
        return sawNegative ? false : null;
    }

    record MajorCriterion(Boolean result, String detail) {
    }

    private enum ClinicalPhase {
        ACUTE("ca cấp"),
        CHRONIC("ca mạn");

        private final String label;

        ClinicalPhase(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
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
