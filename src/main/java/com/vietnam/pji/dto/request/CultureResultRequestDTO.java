package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CultureResultRequestDTO {

    @NotNull(message = "episodeId must not be null")
    private Long episodeId;

    private Integer incubationDays;

    private String name;

    private String result;

    private String gramType;

    private String sampleType;

    private Boolean antibioticed;

    private Integer daysOffAntibio;

    private String notes;
}
