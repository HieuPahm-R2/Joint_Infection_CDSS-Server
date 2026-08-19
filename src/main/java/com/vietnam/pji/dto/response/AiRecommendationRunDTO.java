package com.vietnam.pji.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * Flat projection of {@code AiRecommendationRun} used in API responses.
 *
 * <p>Decouples serialization from the JPA entity so lazy associations
 * ({@code episode}, {@code snapshot}, downstream {@code patient}) never reach
 * Jackson with a stale Hibernate session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationRunDTO implements Serializable {

    private Long id;
    private Long episodeId;
    private Long snapshotId;
    private Integer runNo;
    private String triggerType;
    private String status;
    private String modelName;
    private String modelVersion;
    private Long latencyMs;
    private String errorMessage;
    private String requestId;
    private Map<String, Object> dataCompletenessJson;
    private boolean pendingTasksSaved;
    private Date createdAt;
    private Date updatedAt;
}
