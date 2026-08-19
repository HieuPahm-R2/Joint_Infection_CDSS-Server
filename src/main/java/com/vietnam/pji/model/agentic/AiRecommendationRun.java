package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.constant.TriggerType;
import com.vietnam.pji.model.AbstractEntity;
import com.vietnam.pji.model.medical.PjiEpisode;
import jakarta.persistence.*;
import lombok.*;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_recommendation_runs")
public class AiRecommendationRun extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private PjiEpisode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private CaseClinicalSnapshot snapshot;

    @Column(name = "run_no")
    private Integer runNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 30)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private RunStatus status;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_id")
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_completeness_json", columnDefinition = "jsonb")
    private Map<String, Object> dataCompletenessJson;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    /**
     * True once the doctor has saved this run's completeness gaps as pending
     * lab tasks. Drives the disabled state of the "Lưu nhắc nhở" button so it
     * survives reloads and follows the user across devices.
     */
    @Builder.Default
    @Column(name = "pending_tasks_saved", nullable = false)
    private boolean pendingTasksSaved = false;
}
