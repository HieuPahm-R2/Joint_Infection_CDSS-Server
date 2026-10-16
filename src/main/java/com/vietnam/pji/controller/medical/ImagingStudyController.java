package com.vietnam.pji.controller.medical;

import com.vietnam.pji.dto.request.ImagingStudyRequestDTO;
import com.vietnam.pji.dto.response.ImagingStudyResponseDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.services.ocr.ImagingStudyService;

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
@Tag(name = "Image Results", description = "Imaging study results (X-ray, CT, MRI) attached to an episode")
@RequiredArgsConstructor
public class ImagingStudyController {

    private final ImagingStudyService imageResultService;

    @Operation(summary = "Create image result")
    @PostMapping("/image-results")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<ImagingStudyResponseDTO> createImageResult(@Valid @RequestBody ImagingStudyRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "Image result created successfully",
                imageResultService.create(request));
    }

    @Operation(summary = "Update image result")
    @PutMapping("/image-results/{id}")
    public ResponseData<ImagingStudyResponseDTO> updateImageResult(
            @PathVariable Long id, @Valid @RequestBody ImagingStudyRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "Image result updated successfully",
                imageResultService.update(id, request));
    }

    @Operation(summary = "Get image result by id")
    @GetMapping("/image-results/{id}")
    public ResponseData<ImagingStudyResponseDTO> getImageResult(@PathVariable Long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch image result successfully",
                imageResultService.getById(id));
    }

    @Operation(summary = "Delete image result")
    @DeleteMapping("/image-results/{id}")
    public ResponseData<Void> deleteImageResult(@PathVariable Long id) {
        imageResultService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Image result deleted successfully");
    }

    @Operation(summary = "List image results by episode")
    @GetMapping({"/admissions/{episodeId}/image-results", "/episodes/{episodeId}/image-results"})
    public ResponseData<PaginationResultDTO> getImageResultsByEpisode(
            @PathVariable Long episodeId, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch image results successfully",
                imageResultService.getByEpisode(episodeId, pageable));
    }
}
