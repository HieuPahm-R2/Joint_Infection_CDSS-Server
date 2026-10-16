package com.vietnam.pji.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Single payload for the atomic episode save ({@code POST /episodes/full} and
 * {@code PUT /episodes/{id}/full}).
 *
 * Each child item extends its existing request DTO and adds an optional {@code id}:
 * present id ⇒ update, absent ⇒ create; existing DB rows whose id is missing from
 * the payload ⇒ delete. Sensitivities are nested under their culture so the server
 * resolves freshly-created culture ids internally — no client-side id remapping.
 *
 * Only {@link #episode} is bean-validated ({@code @Valid}); child {@code episodeId}/
 * {@code cultureId} constraints are intentionally NOT cascaded because the server sets
 * those foreign keys from the path / parent.
 */
@Getter
@Setter
public class AdmissionFullRequestDTO {

    @Valid
    @NotNull(message = "episode must not be null")
    private AdmissionRequestDTO episode;

    private MedicalHistoryRequestDTO medicalHistory;

    /** Single clinical record upsert (latest is updated, else created). */
    private ClinicalAssessmentRequestDTO clinicalRecord;

    /** Single lab result upsert (latest is updated, else created). */
    private LabReportRequestDTO labResult;

    private List<SurgeryItem> surgeries;

    private List<ImageItem> images;

    private List<CultureItem> cultures;

    @Getter
    @Setter
    public static class SurgeryItem extends SurgeryRequestDTO {
        private Long id;
    }

    @Getter
    @Setter
    public static class ImageItem extends ImagingStudyRequestDTO {
        private Long id;
    }

    @Getter
    @Setter
    public static class CultureItem extends SpecimenCultureRequestDTO {
        private Long id;
        private List<SensitivityItem> sensitivities;
    }

    @Getter
    @Setter
    public static class SensitivityItem extends AntibioticSusceptibilityRequestDTO {
        private Long id;
    }
}
