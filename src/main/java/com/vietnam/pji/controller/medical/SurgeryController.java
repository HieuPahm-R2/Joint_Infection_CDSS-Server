package com.vietnam.pji.controller.medical;

import com.vietnam.pji.dto.request.SurgeryRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.medical.Surgery;
import com.vietnam.pji.services.medical.SurgeryService;

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
@Tag(name = "Surgeries", description = "Surgery records attached to an episode")
@RequiredArgsConstructor
public class SurgeryController {

    private final SurgeryService surgeryService;

    @Operation(summary = "Create surgery")
    @PostMapping("/surgeries")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<Surgery> createSurgery(@Valid @RequestBody SurgeryRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "Surgery created successfully",
                surgeryService.create(request));
    }

    @Operation(summary = "Update surgery")
    @PutMapping("/surgeries/{id}")
    public ResponseData<Surgery> updateSurgery(
            @PathVariable Long id, @Valid @RequestBody SurgeryRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "Surgery updated successfully",
                surgeryService.update(id, request));
    }

    @Operation(summary = "Get surgery by id")
    @GetMapping("/surgeries/{id}")
    public ResponseData<Surgery> getSurgery(@PathVariable Long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch surgery successfully",
                surgeryService.getById(id));
    }

    @Operation(summary = "Delete surgery")
    @DeleteMapping("/surgeries/{id}")
    public ResponseData<Void> deleteSurgery(@PathVariable Long id) {
        surgeryService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Surgery deleted successfully");
    }

    @Operation(summary = "List surgeries by episode")
    @GetMapping({"/admissions/{episodeId}/surgeries", "/episodes/{episodeId}/surgeries"})
    public ResponseData<PaginationResultDTO> getSurgeriesByEpisode(
            @PathVariable Long episodeId, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch surgeries successfully",
                surgeryService.getByEpisode(episodeId, pageable));
    }
}
