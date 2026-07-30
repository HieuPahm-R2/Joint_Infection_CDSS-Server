package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.constant.DirectEnum;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pji_episodes")
public class PjiEpisode extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Patient patient;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "admission_time")
    private LocalTime admissionTime;

    @Column(name = "discharge_date")
    private LocalDate dischargeDate;

    @Column(name = "discharge_time")
    private LocalTime dischargeTime;

    @Column(name = "admission_count")
    private Integer admissionCount;

    @Column(name = "treatment_days")
    private Integer treatmentDays;

    @Column(name = "initial_department_treatment_days")
    private Integer initialDepartmentTreatmentDays;

    @Column(name = "initial_department_admission_date")
    private LocalDate initialDepartmentAdmissionDate;

    @Column(name = "initial_department_admission_time")
    private LocalTime initialDepartmentAdmissionTime;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "department", length = 255)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "direct")
    private DirectEnum direct;

    @Column(name = "referral_source", length = 255)
    private String referralSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "department_transfers", columnDefinition = "jsonb")
    @Builder.Default
    private List<EpisodeDepartmentTransfer> departmentTransfers = new ArrayList<>();

    @Column(name = "hospital_transfer_type", length = 30)
    private String hospitalTransferType;

    @Column(name = "hospital_transfer_destination", columnDefinition = "TEXT")
    private String hospitalTransferDestination;

    @Column(name = "discharge_disposition", length = 30)
    private String dischargeDisposition;

    @Column(name = "referral_diagnosis", columnDefinition = "TEXT")
    private String referralDiagnosis;

    @Column(name = "emergency_diagnosis", columnDefinition = "TEXT")
    private String emergencyDiagnosis;

    @Column(name = "inpatient_diagnosis", columnDefinition = "TEXT")
    private String inpatientDiagnosis;

    @Column(name = "has_incident")
    private Boolean hasIncident;

    @Column(name = "has_complication")
    private Boolean hasComplication;

    @Column(name = "complication_cause", length = 30)
    private String complicationCause;

    @Column(name = "postoperative_treatment_days")
    private Integer postoperativeTreatmentDays;

    @Column(name = "surgery_count")
    private Integer surgeryCount;

    @Column(name = "discharge_primary_diagnosis", columnDefinition = "TEXT")
    private String dischargePrimaryDiagnosis;

    @Column(name = "discharge_cause", columnDefinition = "TEXT")
    private String dischargeCause;

    @Column(name = "accompanying_disease", columnDefinition = "TEXT")
    private String accompanyingDisease;

    @Column(name = "preoperative_diagnosis", columnDefinition = "TEXT")
    private String preoperativeDiagnosis;

    @Column(name = "postoperative_diagnosis", columnDefinition = "TEXT")
    private String postoperativeDiagnosis;

    @Column(name = "result", length = 200)
    private String result;

    @Column(name = "status", length = 100)
    private String status;
}
