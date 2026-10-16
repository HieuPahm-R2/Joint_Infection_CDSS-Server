package com.vietnam.pji.services.medical.impl.pendingtask;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vietnam.pji.constant.CultureStatus;
import com.vietnam.pji.constant.ImplantType;
import com.vietnam.pji.model.medical.ClinicalRecord;
import com.vietnam.pji.model.medical.CultureResult;
import com.vietnam.pji.model.medical.MedicalHistory;
import com.vietnam.pji.model.medical.Surgery;

import lombok.RequiredArgsConstructor;

/**
 * Decides whether a pending completeness field has already been satisfied by
 * data the clinician entered, so {@code PendingLabTaskServiceImpl} can close the
 * task and the matching logic lives in one testable place.
 *
 * <p>
 * Two independent sources are matched, because the two entry paths look
 * different:
 *
 * <ul>
 * <li><strong>Lab fields</strong> are matched against the normalized row
 * identities (row id, row name) and biochemical keys of a just-saved
 * {@code LabResult}, using the alias table in {@link LabFieldCatalog} shared
 * with the Rag_Agentic completeness check.</li>
 * <li><strong>Clinical fields</strong> (sinus tract, infection type, implant
 * stability, allergies, culture results, histology) are matched against the
 * persisted clinical record, medical history, culture and surgery rows.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class LabFieldFulfillmentDetector {

    /** Completeness fields answered by clinical, culture or surgery records. */
    public static final Set<String> CLINICAL_FIELDS = Set.of(
            "sinus_tract",
            "infection_type",
            "implant_stability",
            "allergies",
            "culture_results",
            "positive_histology");

    private final LabFieldCatalog catalog;

    /**
     * True when a clinician-entered datum (captured as a normalized token from a
     * row id/name or a biochemical key) satisfies the given completeness field.
     * Matches the canonical row id and the diacritic-insensitive name aliases.
     */
    public boolean isLabFieldPresent(String field, Set<String> presentTokens) {
        if (field == null || presentTokens.isEmpty()) {
            return false;
        }
        LabFieldCatalog.LabFieldSpec spec = catalog.declaredSpecFor(field);
        if (spec != null
                && (presentTokens.contains(catalog.normalizeToken(spec.id()))
                        || presentTokens.contains(catalog.normalizeToken(spec.name())))) {
            return true;
        }
        return catalog.aliasesFor(field).stream()
                .anyMatch(alias -> presentTokens.stream().anyMatch(token -> token.contains(alias)));
    }

    /**
     * True when the persisted clinical records answer {@code field}.
     *
     * @param record   latest clinical record of the episode; may be {@code null}
     * @param history  medical history of the episode; may be {@code null}
     * @param cultures culture results of the episode; may be empty
     * @param surgeries surgeries of the episode; may be empty
     */
    public boolean isClinicalFieldSatisfied(String field,
            ClinicalRecord record,
            MedicalHistory history,
            List<CultureResult> cultures,
            List<Surgery> surgeries) {
        if (field == null) {
            return false;
        }
        return switch (field) {
            case "sinus_tract" -> record != null && record.getSinusTract() != null;
            case "infection_type" -> record != null && record.getOnsetTiming() != null;
            case "implant_stability" -> record != null
                    && record.getImplantStability() != null
                    && record.getImplantStability() != ImplantType.UNKNOWN;
            case "allergies" -> history != null && history.getIsAllergy() != null;
            case "culture_results" -> hasUsableCultureResult(cultures);
            case "positive_histology" -> hasHistologyFindings(surgeries);
            default -> false;
        };
    }

    /**
     * Collects normalized tokens (row id, row name, biochemical key) for every
     * filled lab datum, so {@link #isLabFieldPresent} can match against canonical
     * ids and diacritic-insensitive name aliases regardless of how the row was
     * labelled in the form.
     */
    public Set<String> extractPresentFields(List<Map<String, Object>> hematologyTests,
            List<Map<String, Object>> fluidAnalysis,
            Map<String, Object> biochemicalData) {
        Set<String> tokens = new HashSet<>();
        addRowTokens(tokens, hematologyTests);
        addRowTokens(tokens, fluidAnalysis);
        if (biochemicalData != null) {
            for (Map.Entry<String, Object> entry : biochemicalData.entrySet()) {
                if (isBiochemValueFilled(entry.getValue())) {
                    String token = catalog.normalizeToken(entry.getKey());
                    if (!token.isEmpty()) {
                        tokens.add(token);
                    }
                }
            }
        }
        return tokens;
    }

    private void addRowTokens(Set<String> tokens, List<Map<String, Object>> rows) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Object value = row.get("value");
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            for (String key : catalog.rowIdentityKeys()) {
                String token = catalog.normalizeToken(row.get(key));
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        }
    }

    /** A biochemical entry counts as filled unless its {value} wrapper is empty. */
    private boolean isBiochemValueFilled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Map<?, ?> map && map.containsKey("value")) {
            Object inner = map.get("value");
            return inner != null && !inner.toString().isBlank();
        }
        return !value.toString().isBlank();
    }

    private boolean hasHistologyFindings(List<Surgery> surgeries) {
        if (surgeries == null) {
            return false;
        }
        for (Surgery surgery : surgeries) {
            if (surgery.getPositiveHistology() != null) {
                return true;
            }
            String findings = surgery.getFindings() == null ? "" : surgery.getFindings().toLowerCase();
            if (findings.contains("giai phau benh") || findings.contains("giải phẫu bệnh")
                    || findings.contains("sinh thiet") || findings.contains("sinh thiết")
                    || findings.contains("histolog") || findings.contains("patholog")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUsableCultureResult(List<CultureResult> cultures) {
        if (cultures == null) {
            return false;
        }
        for (CultureResult culture : cultures) {
            if (culture.getResult() != null
                    && culture.getResult() != CultureStatus.PENDING
                    && culture.getResult() != CultureStatus.NOT_PERFORMED) {
                return true;
            }
            if (culture.getName() != null && !culture.getName().isBlank()) {
                return true;
            }
        }
        return false;
    }
}
