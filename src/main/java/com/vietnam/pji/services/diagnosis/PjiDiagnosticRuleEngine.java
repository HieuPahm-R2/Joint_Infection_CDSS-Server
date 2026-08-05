package com.vietnam.pji.services.diagnosis;

import org.springframework.stereotype.Service;

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

    public PjiDiagnosticRuleEngine(PjiCultureEvidenceEvaluator cultureEvidenceEvaluator,
            PjiDiagnosticCriteriaEvaluator criteriaEvaluator,
            PjiDiagnosticReportBuilder reportBuilder) {
        this.cultureEvidenceEvaluator = cultureEvidenceEvaluator;
        this.criteriaEvaluator = criteriaEvaluator;
        this.reportBuilder = reportBuilder;
    }

    public DiagnosticResult evaluate(Map<String, Object> snapshot) {
        Map<String, Object> safeSnapshot = snapshot != null ? snapshot : Map.of();
        PjiCultureEvidenceEvaluator.CultureEvidence culture = cultureEvidenceEvaluator.evaluate(safeSnapshot);
        PjiDiagnosticCriteriaEvaluator.MajorCriterion sinus = criteriaEvaluator.evaluateSinusTract(safeSnapshot);
        boolean majorCriteriaMet = sinus.result() == Boolean.TRUE || culture.majorCriteriaMet();
        List<PjiDiagnosticCriteriaEvaluator.CriterionScore> scores = criteriaEvaluator.evaluateMinorCriteria(safeSnapshot, culture);
        int totalMinorScore = scores.stream().mapToInt(PjiDiagnosticCriteriaEvaluator.CriterionScore::scoreAwarded).sum();
        String interpretation = reportBuilder.interpret(majorCriteriaMet, totalMinorScore);
        return reportBuilder.build(safeSnapshot, culture, sinus, scores, majorCriteriaMet, totalMinorScore, interpretation);
    }

    public record DiagnosticResult(String title, Map<String, Object> itemJson,
            Map<String, Object> assessmentJson, Map<String, Object> explanationJson,
            List<Map<String, Object>> warningsJson) {
    }
}
