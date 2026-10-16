package com.vietnam.pji.dto.request;

import com.vietnam.pji.model.medical.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Kafka outbound payload — aggregates all medical data for an episode
 * and sends it to the Agentic RAG team for AI risk prediction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisRequestDTO implements Serializable {

    private Long episodeId;
    private String requestedBy;        // email of the doctor who triggered the analysis

    private MedicalHistory medicalHistory;
    private List<ClinicalAssessment> clinicalRecords;
    private List<Surgery> surgeries;
    private List<LabReport> labResults;
    private List<ImagingStudy> imageResults;

    /**
     * Each SpecimenCulture may have its own list of SensitivityResults,
     * provided separately for easier flat lookup by the AI team.
     */
    private List<SpecimenCulture> cultureResults;
    private List<AntibioticSusceptibility> sensitivityResults;
}
