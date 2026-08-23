package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vietnam.pji.model.AbstractEntity;
import com.vietnam.pji.constant.ClinicalDecisionStatus;
import com.vietnam.pji.model.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "doctor_final_decisions")
public class DoctorFinalDecision extends AbstractEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false, unique = true)
    @JsonIgnore
    private DoctorRecommendationReview review;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false, unique = true)
    @JsonIgnore
    private AiRecommendationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    @JsonIgnore
    private User author;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ClinicalDecisionStatus status = ClinicalDecisionStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diagnosis_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> diagnosisJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "surgery_plan_json", columnDefinition = "jsonb")
    private Map<String, Object> surgeryPlanJson;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
