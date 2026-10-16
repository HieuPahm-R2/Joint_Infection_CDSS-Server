package com.vietnam.pji.services.medical.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vietnam.pji.services.medical.impl.pendingtask.LabFieldCatalog;
import com.vietnam.pji.services.medical.impl.pendingtask.LabFieldFulfillmentDetector;
import org.junit.jupiter.api.Test;

import com.vietnam.pji.constant.CultureStatus;
import com.vietnam.pji.constant.ImplantStability;
import com.vietnam.pji.model.medical.ClinicalAssessment;
import com.vietnam.pji.model.medical.SpecimenCulture;
import com.vietnam.pji.model.medical.MedicalHistory;
import com.vietnam.pji.model.medical.Surgery;

/**
 * Pins the completeness-field knowledge that moved out of
 * {@code PendingLabTaskServiceImpl}: field eligibility, the declared row
 * identity per field, and the matching rules that decide when a pending task is
 * already answered.
 */
class LabFieldFulfillmentDetectorTest {

    private final LabFieldCatalog catalog = new LabFieldCatalog();
    private final LabFieldFulfillmentDetector detector = new LabFieldFulfillmentDetector(catalog);

    @Test
    void onlyWhitelistedCompletenessFieldsCanOpenATask() {
        assertThat(catalog.isEligibleField("serum_CRP")).isTrue();
        assertThat(catalog.isEligibleField("specimen_cultures")).isTrue();
        // The AI may propose anything; an unsupported field must not open a task.
        assertThat(catalog.isEligibleField("serum_procalcitonin")).isFalse();
        assertThat(catalog.isEligibleField("")).isFalse();
    }

    @Test
    void declaredFieldsKeepTheirCanonicalRowAndSection() {
        LabFieldCatalog.LabFieldSpec crp = catalog.declaredSpecFor("serum_CRP");
        assertThat(crp.section()).isEqualTo("hematology");
        assertThat(crp.id()).isEqualTo("ht_extra_crp");
        assertThat(crp.name()).isEqualTo("CRP");

        LabFieldCatalog.LabFieldSpec creatinine = catalog.declaredSpecFor("renal_function");
        assertThat(creatinine.section()).isEqualTo("biochemical");
        assertThat(creatinine.id()).isEqualTo("bc_6");
        // The biochemical section keys off the canonical lowercase name, not the row id.
        assertThat(catalog.biochemKey("renal_function", creatinine)).isEqualTo("creatinine");
        assertThat(catalog.biochemKey("serum_CRP", catalog.specFor("serum_CRP")))
                .isEqualTo("CRP");

        assertThat(catalog.declaredSpecFor("serum_procalcitonin")).isNull();
    }

    @Test
    void unmappedFieldsFallBackToASelfDescribingHematologyRow() {
        LabFieldCatalog.LabFieldSpec fallback = catalog.specFor("serum Procalcitonin!");

        assertThat(fallback.section()).isEqualTo("hematology");
        assertThat(fallback.id()).isEqualTo("ht_extra_serum_procalcitonin");
        assertThat(fallback.name()).isEqualTo("serum Procalcitonin!");
        assertThat(fallback.normalRange()).isEmpty();
        assertThat(fallback.defaultUnit()).isEmpty();
        // A null/unmappable field still has to render, so the spec is never null-named.
        assertThat(catalog.specFor(null).id()).isEqualTo("ht_extra_unknown");
        assertThat(catalog.specFor(null).name()).isEqualTo("Unknown");
    }

    @Test
    void labFieldIsPresentOnCanonicalIdOrDiacriticInsensitiveAlias() {
        // Canonical row id, produced by the quick-entry writer.
        assertThat(detector.isLabFieldPresent("serum_CRP", Set.of("htextracrp"))).isTrue();
        // Alias of a Vietnamese row name, as typed by a clinician.
        assertThat(detector.isLabFieldPresent("serum_ESR", Set.of("maulang"))).isTrue();
        assertThat(detector.isLabFieldPresent("renal_function", Set.of("creatinine"))).isTrue();
        assertThat(detector.isLabFieldPresent("serum_CRP", Set.of("ddimer"))).isFalse();
        assertThat(detector.isLabFieldPresent("serum_CRP", Set.of())).isFalse();
        assertThat(detector.isLabFieldPresent(null, Set.of("crp"))).isFalse();
    }

    @Test
    void extractPresentFieldsReadsRowIdentitiesAndBiochemicalKeysButSkipsBlanks() {
        List<Map<String, Object>> hematology = List.of(
                Map.of("id", "ht_extra_crp", "name", "CRP", "value", "12"),
                Map.of("id", "ht_7", "name", "Máu lắng", "value", "  "));
        List<Map<String, Object>> fluid = List.of(
                Map.of("name", "Bạch cầu (Dịch)", "value", 4000));
        Map<String, Object> biochemical = Map.of(
                "creatinine", Map.of("value", "90", "unit", "µmol/l"),
                "alt", Map.of("value", ""));

        Set<String> tokens = detector.extractPresentFields(hematology, fluid, biochemical);

        assertThat(tokens).contains("htextracrp", "crp", "bachcaudich", "creatinine");
        // Blank-valued rows and empty biochemical wrappers are not evidence.
        assertThat(tokens).doesNotContain("ht7", "maulang", "alt");
    }

    @Test
    void clinicalFieldsAreSatisfiedOnlyByPersistedEvidence() {
        ClinicalAssessment record = ClinicalAssessment.builder()
                .sinusTract(true)
                .implantStability(ImplantStability.UNKNOWN)
                .build();

        assertThat(detector.isClinicalFieldSatisfied("sinus_tract", record, null, List.of(), List.of()))
                .isTrue();
        // An explicit UNKNOWN implant stability is not an answer.
        assertThat(detector.isClinicalFieldSatisfied("implant_stability", record, null, List.of(), List.of()))
                .isFalse();
        assertThat(detector.isClinicalFieldSatisfied("infection_type", record, null, List.of(), List.of()))
                .isFalse();
        assertThat(detector.isClinicalFieldSatisfied("allergies", null, null, List.of(), List.of()))
                .isFalse();
        assertThat(detector.isClinicalFieldSatisfied(null, record, null, List.of(), List.of()))
                .isFalse();

        assertThat(detector.isClinicalFieldSatisfied("allergies", null,
                MedicalHistory.builder().isAllergy(true).build(), List.of(), List.of())).isTrue();
    }

    @Test
    void cultureFieldRequiresAUsableOrNamedCulture() {
        SpecimenCulture pending = SpecimenCulture.builder().result(CultureStatus.PENDING).build();
        SpecimenCulture notPerformed = SpecimenCulture.builder().result(CultureStatus.NOT_PERFORMED).build();
        SpecimenCulture noGrowth = SpecimenCulture.builder().result(CultureStatus.NO_GROWTH).build();
        SpecimenCulture named = SpecimenCulture.builder().result(CultureStatus.PENDING).name("S. aureus").build();

        assertThat(detector.isClinicalFieldSatisfied("specimen_cultures", null, null, List.of(pending), List.of()))
                .isFalse();
        assertThat(detector.isClinicalFieldSatisfied("specimen_cultures", null, null, List.of(notPerformed), List.of()))
                .isFalse();
        assertThat(detector.isClinicalFieldSatisfied("specimen_cultures", null, null, List.of(noGrowth), List.of()))
                .isTrue();
        assertThat(detector.isClinicalFieldSatisfied("specimen_cultures", null, null, List.of(named), List.of()))
                .isTrue();
    }

    @Test
    void histologyFieldMatchesStructuredFlagOrVietnameseFindings() {
        Surgery flagged = Surgery.builder().positiveHistology(true).build();
        Surgery described = Surgery.builder().findings("Kết quả giải phẫu bệnh: viêm mạn tính").build();
        Surgery unrelated = Surgery.builder().findings("Không có gì đặc biệt").build();

        assertThat(detector.isClinicalFieldSatisfied("positive_histology", null, null, List.of(), List.of(flagged)))
                .isTrue();
        assertThat(detector.isClinicalFieldSatisfied("positive_histology", null, null, List.of(), List.of(described)))
                .isTrue();
        assertThat(detector.isClinicalFieldSatisfied("positive_histology", null, null, List.of(), List.of(unrelated)))
                .isFalse();
    }

    @Test
    void normalizeTokenStripsDiacriticsAndPunctuation() {
        assertThat(catalog.normalizeToken("Máu lắng")).isEqualTo("maulang");
        // "Định lượng CRP (Dịch)" -> "dinh luong crp dich" -> "dinhl uongcrpdich" with spaces removed
        assertThat(catalog.normalizeToken("Định lượng CRP (Dịch)")).isEqualTo("dinhl uongcrpdich".replace(" ", ""));
        assertThat(catalog.normalizeToken("S. aureus")).isEqualTo("saureus");
        assertThat(catalog.normalizeToken(null)).isEmpty();
    }
}
