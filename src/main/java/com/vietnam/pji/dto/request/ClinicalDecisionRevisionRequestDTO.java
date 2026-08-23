package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClinicalDecisionRevisionRequestDTO {

    @NotNull
    private Long revision;
}
