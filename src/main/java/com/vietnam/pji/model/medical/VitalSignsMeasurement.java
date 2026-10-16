package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One measurement of a patient's vital signs during an admission.
 *
 * <p>
 * Decision 0010 splits the clinical record in two: this is the 1:N time series,
 * while {@link ClinicalAssessment} keeps the 1:1 assessment. Until this existed,
 * each save overwrote the four vital fields on the assessment, so the clinical
 * picture at an earlier moment could not be reconstructed from the tables at all
 * — which is the reason the AI layer copies the whole record into a snapshot.
 *
 * <p>
 * During the transition the four fields still exist on {@code ClinicalAssessment}
 * as well; the aggregate appends a measurement whenever the numbers change, and a
 * later step moves reads here before those columns are dropped.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vital_signs_measurements")
public class VitalSignsMeasurement extends AbstractEntity<Long> implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Admission episode;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "bmi", precision = 4, scale = 2)
    private BigDecimal bmi;

    @Version
    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
