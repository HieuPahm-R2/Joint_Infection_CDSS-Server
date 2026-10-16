package com.vietnam.pji.controller.medical;

import com.vietnam.pji.dto.request.LabReportRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.medical.LabReport;
import com.vietnam.pji.services.medical.LabReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}")
@Validated
@Tag(name = "Lab Results", description = "Laboratory test results attached to an episode")
@RequiredArgsConstructor
public class LabReportController {

    private final LabReportService labResultService;

    @Operation(summary = "Create lab result")
    @PostMapping("/lab-results")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<LabReport> createLabResult(@Valid @RequestBody LabReportRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "Lab result created successfully",
                labResultService.create(request));
    }

    @Operation(summary = "Update lab result")
    @PutMapping("/lab-results/{id}")
    public ResponseData<LabReport> updateLabResult(
            @PathVariable Long id, @Valid @RequestBody LabReportRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "Lab result updated successfully",
                labResultService.update(id, request));
    }

    @Operation(summary = "Get lab result by id")
    @GetMapping("/lab-results/{id}")
    public ResponseData<LabReport> getLabResult(@PathVariable Long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch lab result successfully",
                labResultService.getById(id));
    }

    @Operation(summary = "Delete lab result")
    @DeleteMapping("/lab-results/{id}")
    public ResponseData<Void> deleteLabResult(@PathVariable Long id) {
        labResultService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Lab result deleted successfully");
    }

    @Operation(summary = "List lab results by episode")
    @GetMapping({"/admissions/{episodeId}/lab-results", "/episodes/{episodeId}/lab-results"})
    public ResponseData<PaginationResultDTO> getLabResultsByEpisode(
            @PathVariable Long episodeId, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch lab results successfully",
                labResultService.getByEpisode(episodeId, pageable));
    }
}
