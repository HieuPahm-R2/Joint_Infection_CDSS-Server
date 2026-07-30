package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.constant.ImplantType;
import com.vietnam.pji.constant.OnsetTiming;
import com.vietnam.pji.constant.SuspectedTransmissionRoute;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clinical_records")
public class ClinicalRecord extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private PjiEpisode episode;

    @Enumerated(EnumType.STRING)
    @Column(name = "onset_timing", length = 30)
    private OnsetTiming onsetTiming;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm; // chiều cao (cm)

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg; // cân nặng (kg)

    @Column(name = "bmi", precision = 4, scale = 2)
    private BigDecimal bmi;

    private Boolean fever;

    private Boolean pain;

    private Boolean erythema; // có ban đỏ

    private Boolean swelling; // sưng tấy

    private Boolean sinusTract; // có đường rò

    @Column(name = "hematogenous_suspected")
    private Boolean hematogenousSuspected; // nghi ngờ lây truyền qua đường máu
    @Column(name = "pmma_allergy")
    private Boolean pmmaAllergy;

    @Enumerated(EnumType.STRING)
    @Column(name = "suspected_transmission_route", length = 30)
    private SuspectedTransmissionRoute suspectedTransmissionRoute;

    private String softTissue; // tình trạng mô mềm

    @Enumerated(EnumType.STRING)
    @Column(name = "implant_stability", columnDefinition = "implant_stability_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ImplantType implantStability; // độ ổn định của cấy ghép

    @Column(name = "prosthesis_joint")
    private String prosthesisJoint;

    @Column(name = "surgical_disease", columnDefinition = "TEXT")
    private String surgicalDisease;

    /**
     * Internal compatibility contract for the clinical snapshot assembler.
     * The public API exposes {@code surgicalDisease}; the diagnostic snapshot
     * keeps its established {@code notations} key until that contract is
     * versioned independently.
     */
    @JsonIgnore
    public String getNotations() {
        return surgicalDisease;
    }

    /**
     * Snapshot compatibility only. The business field has been removed from
     * persistence and public API use; legacy snapshots retain the key with a
     * null value until their schema is versioned.
     */
    @JsonIgnore
    public Integer getDaysSinceIndexArthroplasty() {
        return null;
    }

}
