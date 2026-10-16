package com.vietnam.pji.services.medical.impl.pendingtask;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The static knowledge of "what a completeness field is": its lab row identity,
 * its storage section, the clinician-facing name/unit/range, and the
 * diacritic-insensitive aliases that let an already-entered row satisfy it.
 *
 * <p>
 * Extracted from {@code PendingLabTaskServiceImpl}, which used to own this
 * catalog inline together with five unrelated use cases. The data here is
 * deliberately declarative and has no dependencies, so it is directly
 * unit-testable and is the one place to edit when the Rag_Agentic completeness
 * contract or a frontend row id changes.
 *
 * <p>
 * The alias tables are kept in sync with the Rag_Agentic completeness aliases
 * and the frontend form row names.
 */
@Component
public class LabFieldCatalog {

    /**
     * Maps completeness field keys to the JSONB storage location and canonical
     * frontend identifier inside {@code LabResult}. Sections: {@code hematology}
     * (hematologyTests), {@code fluid} (fluidAnalysis) and {@code biochemical}
     * (biochemicalData).
     *
     * <p>
     * The id matches the canonical frontend row when one exists (e.g. ht_17 for
     * D-dimer); otherwise it uses a {@code {section}_extra_{slug}} prefix so the
     * row still merges into the clinician view as an AI-requested row.
     */
    public record LabFieldSpec(String section, String id, String name,
            String normalRange, String defaultUnit) {
    }

    private static final Map<String, LabFieldSpec> FIELD_TO_LAB_MAPPING = Map.ofEntries(
            Map.entry("serum_CRP",
                    new LabFieldSpec("hematology", "ht_extra_crp", "CRP", "< 5", "mg/L")),
            Map.entry("serum_ESR",
                    new LabFieldSpec("hematology", "ht_7", "Máu lắng", "< 10", "mm")),
            Map.entry("serum_D_Dimer",
                    new LabFieldSpec("hematology", "ht_17", "D-dimer", "< 0.5", "mg/L FEU")),
            Map.entry("serum_IL6",
                    new LabFieldSpec("hematology", "ht_18", "Serum IL-6", "< 7.0", "pg/mL")),
            Map.entry("synovial_WBC",
                    new LabFieldSpec("fluid", "fa_3", "Bạch cầu (Dịch)", "", "Tế bào/Vi trường")),
            Map.entry("synovial_PMN",
                    new LabFieldSpec("fluid", "fa_6", "%PMN (Dịch)", "", "%")),
            Map.entry("synovial_CRP",
                    new LabFieldSpec("fluid", "fa_5", "Định lượng CRP (Dịch)", "< 6.9", "mg/l")),
            Map.entry("synovial_alpha_defensin",
                    new LabFieldSpec("fluid", "fa_extra_alpha_defensin",
                            "Alpha Defensin (dịch)", "< 0.12", "ug/mL")),
            Map.entry("synovial_LE",
                    new LabFieldSpec("fluid", "fa_extra_leukocyte_esterase",
                            "Leukocyte Esterase (dịch)", "10 - 25", "LEU/µL")),
            Map.entry("renal_function",
                    new LabFieldSpec("biochemical", "bc_6", "Creatinine", "59 - 104", "µmol/l")),
            Map.entry("liver_function",
                    new LabFieldSpec("biochemical", "bc_9", "ALT", "35 - 52", "U/L")));

    /**
     * Canonical lowercase biochemical_data keys for fields stored in that
     * section, so the frontend id mapping (creatinine → bc_6, alt → bc_9)
     * resolves and the Rag_Agentic completeness check finds the value.
     */
    private static final Map<String, String> FIELD_TO_BIOCHEM_KEY = Map.of(
            "renal_function", "creatinine",
            "liver_function", "alt");

    private static final Set<String> ELIGIBLE_COMPLETENESS_FIELDS = Set.of(
            "sinus_tract",
            "culture_results",
            "positive_histology",
            "infection_type",
            "implant_stability",
            "allergies",
            "serum_CRP",
            "serum_ESR",
            "serum_D_Dimer",
            "serum_IL6",
            "synovial_WBC",
            "synovial_PMN",
            "synovial_CRP",
            "synovial_alpha_defensin",
            "synovial_LE",
            "renal_function",
            "liver_function");

    private static final Map<String, Set<String>> FIELD_NAME_ALIASES = Map.ofEntries(
            Map.entry("serum_CRP", Set.of("htextracrp", "crp")),
            Map.entry("serum_ESR", Set.of("ht7", "maulang", "esr", "tocdomaulang")),
            Map.entry("serum_D_Dimer", Set.of("ht17", "ddimer")),
            Map.entry("serum_IL6", Set.of("ht18", "il6")),
            Map.entry("synovial_WBC", Set.of("fa3", "synovialwbc", "bachcaudich")),
            Map.entry("synovial_PMN", Set.of("fa6", "synovialpmn", "pmndich")),
            Map.entry("synovial_alpha_defensin",
                    Set.of("faextraalphadefensin", "ht19", "alphadefensin")),
            Map.entry("synovial_LE",
                    Set.of("faextraleukocyteesterase", "ht15", "leukocyteesterase")),
            Map.entry("renal_function", Set.of("bc6", "creatinin", "creatinine", "egfr")),
            Map.entry("liver_function",
                    Set.of("bc8", "bc9", "alt", "ast", "hoatdoalt", "hoatdoast")));

    private static final List<String> ROW_IDENTITY_KEYS = List.of("id", "name");

    /** @return whether the Rag_Agentic completeness check may open a task for this field */
    public boolean isEligibleField(String field) {
        return ELIGIBLE_COMPLETENESS_FIELDS.contains(field);
    }

    /**
     * @return the declared spec for {@code field}, or a self-describing fallback
     *         for a field the AI proposed but that is not in the static mapping
     */
    public LabFieldSpec specFor(String field) {
        LabFieldSpec declared = declaredSpecFor(field);
        return declared != null ? declared : fallbackSpec(field);
    }

    /**
     * @return the declared spec for {@code field}, or {@code null} when the field
     *         has no canonical row identity and must be matched by alias only
     */
    public LabFieldSpec declaredSpecFor(String field) {
        // The mapping is an immutable Map.ofEntries, whose lookup rejects a null key;
        // a completeness payload with a missing "field" reaches here.
        return field == null ? null : FIELD_TO_LAB_MAPPING.get(field);
    }

    /**
     * Builds a self-describing spec for a field the AI proposed but that is not
     * in the static mapping. Defaults to the hematology section since that is the
     * most common shape for serum biomarkers; a clinician can move the row.
     */
    public LabFieldSpec fallbackSpec(String field) {
        String slug = field == null ? ""
                : field.toLowerCase().replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("(^_+|_+$)", "");
        if (slug.isEmpty()) {
            // A null/blank/unmappable field still needs a renderable row: the spec
            // name is serialized and the record is put into a Map, so it must not
            // be null.
            return new LabFieldSpec("hematology", "ht_extra_unknown", "Unknown", "", "");
        }
        return new LabFieldSpec("hematology", "ht_extra_" + slug, field, "", "");
    }

    /**
     * @return the canonical lowercase {@code biochemical_data} key for a
     *         biochemical field, falling back to the spec name for unmapped
     *         fields
     */
    public String biochemKey(String field, LabFieldSpec spec) {
        return FIELD_TO_BIOCHEM_KEY.getOrDefault(field, spec.name());
    }

    /** @return the normalized aliases that satisfy {@code field}; possibly empty */
    public Set<String> aliasesFor(String field) {
        return FIELD_NAME_ALIASES.getOrDefault(field, Set.of());
    }

    /** @return the row keys whose values identify a lab row in the frontend payload */
    public List<String> rowIdentityKeys() {
        return ROW_IDENTITY_KEYS;
    }

    /** Lowercase, strip Vietnamese diacritics, drop non-alphanumerics. */
    public String normalizeToken(Object value) {
        if (value == null) {
            return "";
        }
        String s = Normalizer.normalize(value.toString(), Normalizer.Form.NFD)
                .toLowerCase()
                .replace("đ", "d");
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
