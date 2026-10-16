package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "medical_histories")
public class MedicalHistory extends AbstractEntity<Long> implements Serializable {

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "admission_id")
    private Admission episode;

    @Column(name = "medical_history", columnDefinition = "TEXT")
    private String medicalHistory;

    @Column(name = "process", columnDefinition = "TEXT")
    private String process;

    @Column(name = "antibiotic_history", columnDefinition = "TEXT")
    private String antibioticHistory;

    @Column(name = "is_allergy")
    private Boolean isAllergy;

    @Column(name = "allergy_note")
    private String allergyNote;

    @Column(name = "is_drug")
    private Boolean isDrug;

    @Column(name = "drug_note")
    private String drugNote;

    @Column(name = "is_alcohol")
    private Boolean isAlcohol;

    @Column(name = "alcohol_note")
    private String alcoholNote;

    @Column(name = "is_smoking")
    private Boolean isSmoking;

    @Column(name = "smoking_note")
    private String smokingNote;

    @Column(name = "is_other")
    private Boolean isOther;

    @Column(name = "other_note")
    private String otherNote;
}
