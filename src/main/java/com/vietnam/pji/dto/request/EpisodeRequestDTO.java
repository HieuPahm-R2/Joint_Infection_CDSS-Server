package com.vietnam.pji.dto.request;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.vietnam.pji.constant.DirectEnum;
import com.vietnam.pji.model.medical.EpisodeDepartmentTransfer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
public class EpisodeRequestDTO {

    @NotNull(message = "patientId must not be null")
    private Long patientId;

    @NotNull(message = "admissionDate must not be null")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate admissionDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime admissionTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dischargeDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime dischargeTime;

    private Integer admissionCount;

    private Integer treatmentDays;

    private Integer initialDepartmentTreatmentDays;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate initialDepartmentAdmissionDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime initialDepartmentAdmissionTime;

    private String reason;

    private String department;

    @Enumerated(EnumType.STRING)
    private DirectEnum direct;

    private String referralSource;

    private List<EpisodeDepartmentTransfer> departmentTransfers;

    private String hospitalTransferType;

    private String hospitalTransferDestination;

    private String dischargeDisposition;

    private String referralDiagnosis;

    private String emergencyDiagnosis;

    private String inpatientDiagnosis;

    private Boolean hasIncident;

    private Boolean hasComplication;

    private String complicationCause;

    private Integer postoperativeTreatmentDays;

    private Integer surgeryCount;

    private String dischargePrimaryDiagnosis;

    private String dischargeCause;

    private String accompanyingDisease;

    private String preoperativeDiagnosis;

    private String postoperativeDiagnosis;

    private String result;

    private String status;
}
