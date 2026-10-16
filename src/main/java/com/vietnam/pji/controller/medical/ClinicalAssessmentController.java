package com.vietnam.pji.controller.medical;

import com.vietnam.pji.dto.request.ClinicalAssessmentRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.medical.ClinicalAssessment;
import com.vietnam.pji.services.medical.ClinicalAssessmentService;

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
@Tag(name = "Clinical Records", description = "Clinical notes and findings attached to an episode")
@RequiredArgsConstructor
public class ClinicalAssessmentController {

    private final ClinicalAssessmentService clinicalRecordService;

    @Operation(summary = "Create clinical record")
    @PostMapping("/clinical-records")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<ClinicalAssessment> createClinicalRecord(@Valid @RequestBody ClinicalAssessmentRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "Clinical record created successfully",
                clinicalRecordService.create(request));
    }

    @Operation(summary = "Update clinical record")
    @PutMapping("/clinical-records/{id}")
    public ResponseData<ClinicalAssessment> updateClinicalRecord(
            @PathVariable Long id, @Valid @RequestBody ClinicalAssessmentRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "Clinical record updated successfully",
                clinicalRecordService.update(id, request));
    }

    @Operation(summary = "Get clinical record by id")
    @GetMapping("/clinical-records/{id}")
    public ResponseData<ClinicalAssessment> getClinicalRecord(@PathVariable Long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch clinical record successfully",
                clinicalRecordService.getById(id));
    }

    @Operation(summary = "Delete clinical record")
    @DeleteMapping("/clinical-records/{id}")
    public ResponseData<Void> deleteClinicalRecord(@PathVariable Long id) {
        clinicalRecordService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Clinical record deleted successfully");
    }

    @Operation(summary = "List clinical records by episode", description = "Paginated clinical records belonging to the given episode")
    @GetMapping({"/admissions/{episodeId}/clinical-records", "/episodes/{episodeId}/clinical-records"})
    public ResponseData<PaginationResultDTO> getClinicalRecordsByEpisode(
            @PathVariable Long episodeId, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch clinical records successfully",
                clinicalRecordService.getByEpisode(episodeId, pageable));
    }
}
