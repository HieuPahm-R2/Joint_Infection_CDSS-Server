package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AntibioticSusceptibilityRequestDTO {

    @NotNull(message = "cultureId must not be null")
    private Long cultureId;

    @NotBlank(message = "antibioticName must not be blank")
    private String antibioticName;

    private String micValue;

    private String sensitivityCode;
}
