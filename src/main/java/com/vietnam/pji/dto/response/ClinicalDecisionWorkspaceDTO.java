package com.vietnam.pji.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDecisionWorkspaceDTO implements Serializable {

    private Long episodeId;
    private Long finalRunId;
    private Long finalDoctorRunId;
    private Long finalPharmacistRunId;
    private List<RunDecision> runs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunDecision implements Serializable {
        private AiRecommendationRunDTO run;
        private DoctorDecision doctorDecision;
        private PharmacistDecision pharmacistDecision;
        private boolean finalSelection;
        private boolean eligibleForFinal;
        private boolean canEditDoctor;
        private boolean canEditPharmacist;
        private boolean canSelectFinal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Actor implements Serializable {
        private Long userId;
        private String fullName;
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoctorDecision implements Serializable {
        private Long id;
        private String status;
        private Actor author;
        private Map<String, Object> diagnosisJson;
        private Map<String, Object> surgeryPlanJson;
        private Instant signedAt;
        private Long revision;
        private Date createdAt;
        private Date updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PharmacistDecision implements Serializable {
        private Long id;
        private String status;
        private Actor author;
        private Map<String, Object> systemicAntibioticPlanJson;
        private Map<String, Object> localAntibioticPlanJson;
        private Map<String, Object> carePlanJson;
        private String notes;
        private Instant signedAt;
        private Long revision;
        private Date createdAt;
        private Date updatedAt;
    }
}
