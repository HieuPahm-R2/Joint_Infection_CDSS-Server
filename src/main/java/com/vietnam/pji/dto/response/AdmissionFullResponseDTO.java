package com.vietnam.pji.dto.response;

import com.vietnam.pji.model.medical.ClinicalAssessment;
import com.vietnam.pji.model.medical.SpecimenCulture;
import com.vietnam.pji.model.medical.LabReport;
import com.vietnam.pji.model.medical.MedicalHistory;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.model.medical.AntibioticSusceptibility;
import com.vietnam.pji.model.medical.Surgery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Full episode aggregate returned by {@code GET /episodes/{id}/full}.
 *
 * Holds the raw entities the editor UI already consumes (so the frontend needs no
 * re-mapping), assembled in a single transactional read. Distinct from the AI
 * {@code AdmissionSnapshotAssemblerService} payload, which is lossy, snake-cased and
 * Redis-cached — unsuitable for an edit screen.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionFullResponseDTO {

    private Admission episode;

    private MedicalHistory medicalHistory;

    /** Latest clinical record (the editor edits a single record). */
    private ClinicalAssessment clinicalRecord;

    private List<Surgery> surgeries;

    private List<LabReport> labResults;

    /** Image results carry a freshly-signed MinIO {@code url} per response. */
    private List<ImagingStudyResponseDTO> imageResults;

    private List<SpecimenCulture> cultureResults;

    /** Sensitivities keyed by cultureId — mirrors the frontend's sensitivityMap. */
    private Map<Long, List<AntibioticSusceptibility>> sensitivityMap;
}
