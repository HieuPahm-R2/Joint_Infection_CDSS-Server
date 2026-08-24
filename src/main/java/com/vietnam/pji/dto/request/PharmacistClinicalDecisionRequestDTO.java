package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class PharmacistClinicalDecisionRequestDTO {

    private Map<String, Object> systemicAntibioticPlanJson;

    private Map<String, Object> localAntibioticPlanJson;

    private Map<String, Object> carePlanJson;

    private String notes;

    @NotNull
    private Long revision;
}
