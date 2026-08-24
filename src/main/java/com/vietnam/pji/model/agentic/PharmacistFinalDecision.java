package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vietnam.pji.constant.ClinicalDecisionStatus;
import com.vietnam.pji.model.AbstractEntity;
import com.vietnam.pji.model.auth.User;
import jakarta.persistence.*;
import lombok.*;
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
@Table(name = "pharmacist_final_decisions")
public class PharmacistFinalDecision extends AbstractEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false, unique = true)
    @JsonIgnore
    private AiRecommendationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    @JsonIgnore
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ClinicalDecisionStatus status = ClinicalDecisionStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "systemic_antibiotic_plan_json", columnDefinition = "jsonb")
    private Map<String, Object> systemicAntibioticPlanJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "local_antibiotic_plan_json", columnDefinition = "jsonb")
    private Map<String, Object> localAntibioticPlanJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "care_plan_json", columnDefinition = "jsonb")
    private Map<String, Object> carePlanJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
