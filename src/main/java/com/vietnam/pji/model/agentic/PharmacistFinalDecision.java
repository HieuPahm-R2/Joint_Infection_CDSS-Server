package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
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
    @JoinColumn(name = "review_id", nullable = false, unique = true)
    @JsonIgnore
    private DoctorRecommendationReview review;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "systemic_antibiotic_plan_json", columnDefinition = "jsonb")
    private Map<String, Object> systemicAntibioticPlanJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "local_antibiotic_plan_json", columnDefinition = "jsonb")
    private Map<String, Object> localAntibioticPlanJson;

    /** Snapshot of culture-linked SensitivityResult rows used for this decision. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sensitivity_results_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> sensitivityResultsJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
