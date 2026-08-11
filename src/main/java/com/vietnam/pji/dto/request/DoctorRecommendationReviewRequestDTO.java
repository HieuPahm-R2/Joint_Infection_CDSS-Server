package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class DoctorRecommendationReviewRequestDTO {

    @NotNull(message = "runId must not be null")
    private Long runId;

    @NotBlank(message = "reviewStatus must not be blank")
    private String reviewStatus;

    private String reviewNote;

    private Map<String, Object> modificationJson;

    private String rejectionReason;

    /** Doctor's own final diagnosis (Chẩn đoán bác sĩ step). */
    private Map<String, Object> doctorDiagnosisJson;

    /** Structured doctor-owned conclusion and surgery plan for this run version. */
    @Valid
    private DoctorFinalDecisionRequestDTO doctorFinalDecision;

    /** When true, this review replaces any previously selected version in the episode. */
    private Boolean selectAsFinalDecision;
}
