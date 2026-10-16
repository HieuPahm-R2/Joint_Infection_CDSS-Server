package com.vietnam.pji.controller.medical;

import com.turkraft.springfilter.boot.Filter;
import com.vietnam.pji.dto.request.AdmissionFullRequestDTO;
import com.vietnam.pji.dto.request.AdmissionRequestDTO;
import com.vietnam.pji.dto.response.AdmissionFullResponseDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.services.episode.AdmissionAggregateService;
import com.vietnam.pji.services.episode.AdmissionService;
import com.vietnam.pji.services.episode.AdmissionLockService;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.utils.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}")
@Validated
@Tag(name = "Episodes", description = "PJI episodes — the clinical case unit that aggregates records, labs, surgeries, and AI runs")
@RequiredArgsConstructor
public class AdmissionController {

    private final AdmissionService episodeService;
    private final AdmissionAggregateService episodeAggregateService;
    private final AdmissionLockService episodeLockService;

    @Operation(summary = "Create episode")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Episode created"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PostMapping({"/admissions", "/episodes"})
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<Admission> createEpisode(@Valid @RequestBody AdmissionRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "Episode created successfully",
                episodeService.create(request));
    }

    @Operation(summary = "Update episode")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Episode updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Episode not found")
    })
    @PutMapping({"/admissions/{id}", "/episodes/{id}"})
    public ResponseData<Admission> updateEpisode(@PathVariable Long id,
            @Valid @RequestBody AdmissionRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "Episode updated successfully",
                episodeService.update(id, request));
    }

    @Operation(summary = "Get episode by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Episode detail"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Episode not found")
    })
    @GetMapping({"/admissions/{id}", "/episodes/{id}"})
    public ResponseData<Admission> getEpisode(@PathVariable Long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch episode successfully", episodeService.getById(id));
    }

    @Operation(summary = "Delete episode")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Episode deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Episode not found")
    })
    @DeleteMapping({"/admissions/{id}", "/episodes/{id}"})
    public ResponseData<Void> deleteEpisode(@PathVariable Long id) {
        episodeService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Episode deleted successfully");
    }

    @Operation(summary = "List episodes", description = "Paginated episode list with springfilter support")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated episode list"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @GetMapping({"/admissions", "/episodes"})
    public ResponseData<PaginationResultDTO> getAllEpisodes(
            @Filter Specification<Admission> spec, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch episodes successfully",
                episodeService.getAll(spec, pageable));
    }

    @Operation(summary = "List episodes by patient", description = "Paginated episodes belonging to the given patient")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated episodes for the patient"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @GetMapping({"/patients/{patientId}/admissions", "/patients/{patientId}/episodes"})
    public ResponseData<PaginationResultDTO> getEpisodesByPatient(
            @PathVariable Long patientId, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch patient episodes successfully",
                episodeService.getByPatient(patientId, pageable));
    }

    @Operation(summary = "Get full episode aggregate",
            description = "Episode plus medical history, clinical record, surgeries, labs, images and cultures/sensitivities — one transactional read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Full episode aggregate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Episode not found")
    })
    @GetMapping({"/admissions/{id}/full", "/episodes/{id}/full"})
    public ResponseData<AdmissionFullResponseDTO> getEpisodeFull(@PathVariable Long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch full episode successfully",
                episodeAggregateService.getFull(id));
    }

    @Operation(summary = "Create full episode aggregate",
            description = "Atomically create an episode and all its child records in one transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Episode and all child records created atomically"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PostMapping({"/admissions/full", "/episodes/full"})
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<AdmissionFullResponseDTO> createEpisodeFull(
            @Valid @RequestBody AdmissionFullRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "Episode created successfully",
                episodeAggregateService.saveFull(null, request));
    }

    @Operation(summary = "Update full episode aggregate",
            description = "Atomically upsert/diff an episode and all its child records in one transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Episode aggregate updated; response contains status and message only"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Episode not found")
    })
    @PutMapping({"/admissions/{id}/full", "/episodes/{id}/full"})
    public ResponseData<Void> updateEpisodeFull(
            @PathVariable Long id, @Valid @RequestBody AdmissionFullRequestDTO request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ForbiddenException("Authenticated user required");
        }
        episodeLockService.assertHeldBy(id, userId);
        episodeAggregateService.updateFull(id, request);
        return new ResponseData<>(HttpStatus.OK.value(), "Episode updated successfully");
    }
}
