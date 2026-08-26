package com.vietnam.pji.services.diagnosis;

import com.vietnam.pji.dto.request.PjiDiagnosticEvaluationRequestDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stable Spring service boundary for deterministic PJI diagnostics.
 *
 * <p>Snapshot parsing, criteria calculation, culture interpretation, and API
 * report construction live in focused collaborators so this facade only
 * orchestrates the evaluation.
 */
@Service
public class PjiDiagnosticRuleEngine {

    private final PjiCultureEvidenceEvaluator cultureEvidenceEvaluator;
    private final PjiDiagnosticCriteriaEvaluator criteriaEvaluator;
    private final PjiDiagnosticReportBuilder reportBuilder;
    private final PjiDiagnosticRequestAdapter requestAdapter;

    @Autowired
    public PjiDiagnosticRuleEngine(PjiCultureEvidenceEvaluator cultureEvidenceEvaluator,
            PjiDiagnosticCriteriaEvaluator criteriaEvaluator,
            PjiDiagnosticReportBuilder reportBuilder,
            PjiDiagnosticRequestAdapter requestAdapter) {
        this.cultureEvidenceEvaluator = cultureEvidenceEvaluator;
        this.criteriaEvaluator = criteriaEvaluator;
        this.reportBuilder = reportBuilder;
        this.requestAdapter = requestAdapter;
    }

    PjiDiagnosticRuleEngine(PjiCultureEvidenceEvaluator cultureEvidenceEvaluator,
            PjiDiagnosticCriteriaEvaluator criteriaEvaluator,
            PjiDiagnosticReportBuilder reportBuilder) {
        this(cultureEvidenceEvaluator, criteriaEvaluator, reportBuilder, new PjiDiagnosticRequestAdapter());
    }

    /** Evaluates an episode snapshot, whose PJI episode context implies eligibility. */
    public DiagnosticResult evaluate(Map<String, Object> snapshot) {
        return evaluateSnapshot(snapshot != null ? snapshot : Map.of(), Boolean.TRUE);
    }

    /** Evaluates the stateless manual-calculator contract through the same engine. */
    public DiagnosticResult evaluate(PjiDiagnosticEvaluationRequestDTO request) {
        Map<String, Object> snapshot = requestAdapter.toSnapshot(request);
        return evaluateSnapshot(snapshot, request.previousArthroplasty());
    }

    private DiagnosticResult evaluateSnapshot(Map<String, Object> safeSnapshot, Boolean eligible) {
        PjiCultureEvidenceEvaluator.CultureEvidence culture = cultureEvidenceEvaluator.evaluate(safeSnapshot);
        PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus = criteriaEvaluator.evaluateSinusTract(safeSnapshot);
        boolean majorCriteriaMet = sinus.result() == Boolean.TRUE || culture.majorCriteriaMet();
        PjiDiagnosticCriteriaEvaluator.CriteriaEvaluation criteria = criteriaEvaluator.evaluateMinorCriteria(safeSnapshot, culture);
        int preoperativeScore = score(criteria.preoperative());
        int intraoperativeScore = score(criteria.intraoperative());
        int combinedScore = preoperativeScore + intraoperativeScore;
        boolean majorComplete = sinus.result() != null && culture.performed() == Boolean.TRUE;
        boolean preoperativeComplete = complete(criteria.preoperative());
        boolean intraoperativeComplete = complete(criteria.intraoperative());
        String interpretation = interpret(eligible, majorCriteriaMet, majorComplete, preoperativeComplete,
                intraoperativeComplete, preoperativeScore, combinedScore);
        String decisionStage = decisionStage(eligible, majorCriteriaMet, preoperativeComplete, preoperativeScore);
        int decisionScore = "COMBINED".equals(decisionStage) ? combinedScore : preoperativeScore;
        String infectionClassification = criteriaEvaluator.infectionClassification(safeSnapshot);
        List<String> missingEvidence = missingEvidence(eligible, majorCriteriaMet, sinus, culture, criteria,
                preoperativeScore);
        List<String> limitations = criteria.acute()
                ? List.of("Ngưỡng cho ca cấp (<90 ngày) là ngưỡng đề xuất ICM 2018 và chưa được thẩm định trong định nghĩa điểm 2018.")
                : List.of();
        EvaluationSummary summary = new EvaluationSummary(interpretation, eligible, majorComplete,
                preoperativeComplete, intraoperativeComplete, preoperativeScore, intraoperativeScore,
                combinedScore, decisionScore, decisionStage, missingEvidence, limitations, infectionClassification);
        return reportBuilder.build(safeSnapshot, culture, sinus, criteria.all(), majorCriteriaMet, summary);
    }

    private int score(List<PjiDiagnosticCriteriaEvaluator.CriterionScore> criteria) {
        return criteria.stream().mapToInt(PjiDiagnosticCriteriaEvaluator.CriterionScore::scoreAwarded).sum();
    }

    private boolean complete(List<PjiDiagnosticCriteriaEvaluator.CriterionScore> criteria) {
        return criteria.stream().filter(PjiDiagnosticCriteriaEvaluator.CriterionScore::applicable)
                .allMatch(item -> item.result() != null);
    }

    private String interpret(Boolean eligible, boolean majorMet, boolean majorComplete, boolean preoperativeComplete,
            boolean intraoperativeComplete, int preoperativeScore, int combinedScore) {
        if (eligible == Boolean.FALSE) return "NOT_APPLICABLE";
        if (eligible == null) return "INCOMPLETE";
        if (majorMet || preoperativeScore >= 6) return "INFECTED";
        if (!majorComplete || !preoperativeComplete) return "INCOMPLETE";
        if (preoperativeScore <= 1) return "NOT_INFECTED";
        if (combinedScore >= 6) return "INFECTED";
        if (!intraoperativeComplete) return "INCOMPLETE";
        return combinedScore >= 4 ? "INCONCLUSIVE" : "NOT_INFECTED";
    }

    private String decisionStage(Boolean eligible, boolean majorMet, boolean preoperativeComplete,
            int preoperativeScore) {
        if (eligible == Boolean.FALSE || eligible == null) return "ELIGIBILITY";
        if (majorMet) return "MAJOR";
        if (preoperativeComplete && preoperativeScore >= 2 && preoperativeScore <= 5) return "COMBINED";
        return "PREOPERATIVE";
    }

    private List<String> missingEvidence(Boolean eligible, boolean majorCriteriaMet,
            PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus,
            PjiCultureEvidenceEvaluator.CultureEvidence culture,
            PjiDiagnosticCriteriaEvaluator.CriteriaEvaluation criteria,
            int preoperativeScore) {
        List<String> missing = new ArrayList<>();
        if (eligible == null) missing.add("eligibility.previous_arthroplasty");
        if (eligible != Boolean.TRUE || majorCriteriaMet || preoperativeScore >= 6) return missing;
        if (sinus.result() == null) missing.add("major.sinus_tract");
        if (culture.performed() != Boolean.TRUE) missing.add("major.culture_results");
        criteria.preoperative().stream()
                .filter(PjiDiagnosticCriteriaEvaluator.CriterionScore::applicable)
                .filter(item -> item.result() == null)
                .forEach(item -> missing.add("preoperative." + item.criterion()));
        if (preoperativeScore >= 2 && preoperativeScore <= 5) {
            criteria.intraoperative().stream()
                    .filter(PjiDiagnosticCriteriaEvaluator.CriterionScore::applicable)
                    .filter(item -> item.result() == null)
                    .forEach(item -> missing.add("intraoperative." + item.criterion()));
        }
        return missing;
    }

    record EvaluationSummary(String interpretation, Boolean eligible, boolean majorComplete,
            boolean preoperativeComplete, boolean intraoperativeComplete, int preoperativeScore,
            int intraoperativeScore, int combinedScore, int decisionScore, String decisionStage, List<String> missingEvidence,
            List<String> limitations, String infectionClassification) {
        boolean completeForConclusion() {
            return !"INCOMPLETE".equals(interpretation);
        }
    }

    public record DiagnosticResult(String title, Map<String, Object> itemJson,
            Map<String, Object> assessmentJson, Map<String, Object> explanationJson) {
    }
}
