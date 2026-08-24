package com.vietnam.pji.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** Stateless payload sent to the dedicated RAG antibiotic care-plan workflow. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAntibioticCarePlanRequestDTO implements Serializable {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("episode_id")
    private Long episodeId;

    @JsonProperty("snapshot_data_json")
    private Map<String, Object> snapshotDataJson;

    @JsonProperty("systemic_antibiotic_plan")
    private Map<String, Object> systemicAntibioticPlan;

    @JsonProperty("local_antibiotic_plan")
    private Map<String, Object> localAntibioticPlan;
}
