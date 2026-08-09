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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diagnosis_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> diagnosisJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "surgery_plan_json", columnDefinition = "jsonb")
    private Map<String, Object> surgeryPlanJson;
}
