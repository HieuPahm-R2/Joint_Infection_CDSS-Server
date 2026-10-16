package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class LabReportRequestDTO {

    @NotNull(message = "episodeId must not be null")
    private Long episodeId;

    private List<Map<String, Object>> hematologyTests;
    private List<Map<String, Object>> fluidAnalysis;
    private Map<String, Object> biochemicalData;

}
