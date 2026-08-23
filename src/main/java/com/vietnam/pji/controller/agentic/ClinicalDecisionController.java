package com.vietnam.pji.controller.agentic;

import com.vietnam.pji.dto.request.ClinicalDecisionRevisionRequestDTO;
import com.vietnam.pji.dto.request.DoctorClinicalDecisionRequestDTO;
import com.vietnam.pji.dto.request.PharmacistClinicalDecisionRequestDTO;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO.RunDecision;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.services.clinicaldecision.ClinicalDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}")
@Validated
@RequiredArgsConstructor
@Tag(name = "Clinical Decisions", description = "Run-scoped doctor and pharmacist decisions")
public class ClinicalDecisionController {

    private final ClinicalDecisionService clinicalDecisionService;

    @GetMapping("/episodes/{episodeId}/clinical-decisions/workspace")
    @Operation(summary = "Get all AI runs and decision status for an episode")
    public ResponseData<ClinicalDecisionWorkspaceDTO> getWorkspace(@PathVariable Long episodeId) {
        return ok("Fetch clinical decision workspace successfully",
                clinicalDecisionService.getWorkspace(episodeId));
    }

    @GetMapping("/ai-recommendations/runs/{runId}/clinical-decisions")
    @Operation(summary = "Get doctor and pharmacist decisions for one AI run")
    public ResponseData<RunDecision> getRunDecision(@PathVariable Long runId) {
        return ok("Fetch run clinical decisions successfully",
                clinicalDecisionService.getRunDecision(runId));
    }

    @PutMapping("/ai-recommendations/runs/{runId}/doctor-decision")
    @Operation(summary = "Create or update the owning doctor's draft")
    public ResponseData<RunDecision> saveDoctorDecision(
            @PathVariable Long runId,
            @Valid @RequestBody DoctorClinicalDecisionRequestDTO request) {
        return ok("Doctor decision saved successfully",
                clinicalDecisionService.saveDoctorDecision(runId, request));
    }

    @PostMapping("/ai-recommendations/runs/{runId}/doctor-decision/sign")
    @Operation(summary = "Sign and lock the owning doctor's decision")
    public ResponseData<RunDecision> signDoctorDecision(
            @PathVariable Long runId,
            @Valid @RequestBody ClinicalDecisionRevisionRequestDTO request) {
        return ok("Doctor decision signed successfully",
                clinicalDecisionService.signDoctorDecision(runId, request));
    }

    @PutMapping("/ai-recommendations/runs/{runId}/pharmacist-decision")
    @Operation(summary = "Claim or update the owning pharmacist's draft")
    public ResponseData<RunDecision> savePharmacistDecision(
            @PathVariable Long runId,
            @Valid @RequestBody PharmacistClinicalDecisionRequestDTO request) {
        return ok("Pharmacist decision saved successfully",
                clinicalDecisionService.savePharmacistDecision(runId, request));
    }

    @PostMapping("/ai-recommendations/runs/{runId}/pharmacist-decision/sign")
    @Operation(summary = "Sign and lock the owning pharmacist's decision")
    public ResponseData<RunDecision> signPharmacistDecision(
            @PathVariable Long runId,
            @Valid @RequestBody ClinicalDecisionRevisionRequestDTO request) {
        return ok("Pharmacist decision signed successfully",
                clinicalDecisionService.signPharmacistDecision(runId, request));
    }

    @PutMapping("/episodes/{episodeId}/clinical-decisions/final-run/{runId}")
    @Operation(summary = "Select a fully signed AI run as the episode final version")
    public ResponseData<RunDecision> selectFinalRun(
            @PathVariable Long episodeId,
            @PathVariable Long runId) {
        return ok("Final recommendation run selected successfully",
                clinicalDecisionService.selectFinalRun(episodeId, runId));
    }

    private <T> ResponseData<T> ok(String message, T data) {
        return new ResponseData<>(HttpStatus.OK.value(), message, data);
    }
}
