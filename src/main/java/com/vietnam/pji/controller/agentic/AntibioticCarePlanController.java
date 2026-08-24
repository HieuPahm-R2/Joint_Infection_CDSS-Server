package com.vietnam.pji.controller.agentic;

import com.vietnam.pji.dto.response.AntibioticCarePlanResponseDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.services.antibiotic.AntibioticCarePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}")
@Validated
@RequiredArgsConstructor
@Tag(name = "Antibiotic Care Plan", description = "Stateless pharmacist care-plan generation")
public class AntibioticCarePlanController {

    private final AntibioticCarePlanService antibioticCarePlanService;

    @PostMapping("/episodes/{episodeId}/antibiotic-care-plan/generate")
    @Operation(summary = "Generate a care plan from the latest signed pharmacist regimen")
    public ResponseData<AntibioticCarePlanResponseDTO> generate(@PathVariable Long episodeId) {
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Antibiotic care plan generated successfully",
                antibioticCarePlanService.generate(episodeId));
    }
}
