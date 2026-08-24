package com.vietnam.pji.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** Public, non-persisted care-plan response used by the pharmacist workspace. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AntibioticCarePlanResponseDTO implements Serializable {
    private String requestId;
    private String status;
    private AiAntibioticCarePlanResponseDTO.ModelInfo model;
    private Long latencyMs;
    private Long episodeId;
    private Long sourceRunId;
    private Integer sourceRunNo;
    private String pharmacistName;
    private Map<String, Object> carePlan;
    private List<AiAntibioticCarePlanResponseDTO.Citation> citations;
}
