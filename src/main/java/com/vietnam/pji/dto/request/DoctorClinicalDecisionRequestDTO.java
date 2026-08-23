package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class DoctorClinicalDecisionRequestDTO {

    @NotNull
    private Map<String, Object> diagnosisJson;

    private Map<String, Object> surgeryPlanJson;

    @NotNull
    private Long revision;
}
