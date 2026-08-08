package com.vietnam.pji.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class SurgeryRequestDTO {

    @NotNull(message = "episodeId must not be null")
    private Long episodeId;

    @NotNull(message = "surgeryDate must not be null")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonFormat(pattern = "dd-MM-yyyy")
    private LocalDate surgeryDate;

    @NotBlank(message = "surgeryType must not be blank")
    private String surgeryType;

    private String woundStatus;

    private String findings;

    private Boolean positiveHistology;

    private Boolean intraoperativePurulence;
}
