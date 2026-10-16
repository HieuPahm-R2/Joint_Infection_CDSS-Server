package com.vietnam.pji.controller.medical;

import com.vietnam.pji.dto.request.MedicalHistoryRequestDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.medical.MedicalHistory;
import com.vietnam.pji.services.medical.MedicalHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"${api.prefix}/admissions/{episodeId}/medical-history", "${api.prefix}/episodes/{episodeId}/medical-history"})
@Validated
@Tag(name = "Medical History", description = "Patient medical history for a given episode")
@RequiredArgsConstructor
public class MedicalHistoryController {

    private final MedicalHistoryService medicalHistoryService;

    @Operation(summary = "Create medical history", description = "Creates the medical history record for the given episode")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<MedicalHistory> createMedicalHistory(
            @PathVariable Long episodeId,
            @RequestBody MedicalHistoryRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "Medical history created successfully",
                medicalHistoryService.create(episodeId, request));
    }

    @Operation(summary = "Update medical history")
    @PutMapping
    public ResponseData<MedicalHistory> updateMedicalHistory(
            @PathVariable Long episodeId,
            @RequestBody MedicalHistoryRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "Medical history updated successfully",
                medicalHistoryService.update(episodeId, request));
    }

    @Operation(summary = "Get medical history by episode")
    @GetMapping
    public ResponseData<MedicalHistory> getMedicalHistory(@PathVariable Long episodeId) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch medical history successfully",
                medicalHistoryService.getByEpisodeId(episodeId));
    }
}
