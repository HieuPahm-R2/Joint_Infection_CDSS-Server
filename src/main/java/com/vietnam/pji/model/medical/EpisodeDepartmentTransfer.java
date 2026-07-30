package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeDepartmentTransfer implements Serializable {

    private String department;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate admissionDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime admissionTime;

    private Integer treatmentDays;
}
