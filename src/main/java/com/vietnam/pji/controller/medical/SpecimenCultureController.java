package com.vietnam.pji.controller.medical;

import com.vietnam.pji.dto.request.SpecimenCultureRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.medical.SpecimenCulture;
import com.vietnam.pji.services.medical.SpecimenCultureService;

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
@Tag(name = "Culture Results", description = "Microbiology culture results attached to an episode")
@RequiredArgsConstructor
public class SpecimenCultureController {

    private final SpecimenCultureService cultureResultService;

    @Operation(summary = "Create culture result")
    @PostMapping("/culture-results")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<SpecimenCulture> createCultureResult(@Valid @RequestBody SpecimenCultureRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "Culture result created successfully",
                cultureResultService.create(request));
    }

    @Operation(summary = "Update culture result")
    @PutMapping("/culture-results/{id}")
    public ResponseData<SpecimenCulture> updateCultureResult(
            @PathVariable Long id, @Valid @RequestBody SpecimenCultureRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "Culture result updated successfully",
                cultureResultService.update(id, request));
    }

    @Operation(summary = "Get culture result by id")
    @GetMapping("/culture-results/{id}")
    public ResponseData<SpecimenCulture> getCultureResult(@PathVariable Long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch culture result successfully",
                cultureResultService.getById(id));
    }

    @Operation(summary = "Delete culture result")
    @DeleteMapping("/culture-results/{id}")
    public ResponseData<Void> deleteCultureResult(@PathVariable Long id) {
        cultureResultService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Culture result deleted successfully");
    }

    @Operation(summary = "List culture results by episode", description = "Paginated culture results belonging to the given episode")
    @GetMapping({"/admissions/{episodeId}/culture-results", "/episodes/{episodeId}/culture-results"})
    public ResponseData<PaginationResultDTO> getCultureResultsByEpisode(
            @PathVariable Long episodeId, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch culture results successfully",
                cultureResultService.getByEpisode(episodeId, pageable));
    }
}
