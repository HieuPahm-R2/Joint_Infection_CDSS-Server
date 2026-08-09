package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class DoctorFinalDecisionRequestDTO {

    @NotEmpty(message = "diagnosisJson must not be empty")
    private Map<String, Object> diagnosisJson;

    private Map<String, Object> surgeryPlanJson;
}
