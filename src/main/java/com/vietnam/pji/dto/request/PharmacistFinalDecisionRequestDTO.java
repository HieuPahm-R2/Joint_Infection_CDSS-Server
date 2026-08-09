package com.vietnam.pji.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class PharmacistFinalDecisionRequestDTO {

    private Map<String, Object> systemicAntibioticPlanJson;
    private Map<String, Object> localAntibioticPlanJson;
    private List<Map<String, Object>> sensitivityResultsJson;
    private String notes;
}
