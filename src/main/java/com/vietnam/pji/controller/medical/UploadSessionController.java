package com.vietnam.pji.controller.medical;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.vietnam.pji.dto.request.CompleteUploadSessionRequestDTO;
import com.vietnam.pji.dto.request.CreateUploadSessionRequestDTO;
import com.vietnam.pji.dto.request.PresignUploadFileRequestDTO;
import com.vietnam.pji.dto.response.CreateUploadSessionResponseDTO;
import com.vietnam.pji.dto.response.PresignedUploadResponseDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.dto.response.UploadSessionCompleteResponseDTO;
import com.vietnam.pji.dto.response.UploadSessionValidationResponseDTO;
import com.vietnam.pji.services.upload.UploadSessionService;
import com.vietnam.pji.utils.SecurityUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
@Tag(name = "Upload Sessions", description = "Short-lived QR upload sessions for clinical images")
public class UploadSessionController {

    private final UploadSessionService uploadSessionService;

    @PostMapping("/patients/{patientId}/upload-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<CreateUploadSessionResponseDTO> create(
            @PathVariable Long patientId,
            @Valid @RequestBody CreateUploadSessionRequestDTO request) {
        String requestBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();
        CreateUploadSessionResponseDTO data = uploadSessionService.create(
                patientId,
                request.episodeId(),
                SecurityUtils.getCurrentUserId(),
                requestBaseUrl);
        return new ResponseData<>(HttpStatus.CREATED.value(), "Upload session created", data);
    }

    @GetMapping("/upload-sessions/{sessionId}/validate")
    public ResponseData<UploadSessionValidationResponseDTO> validate(
            @PathVariable UUID sessionId,
            @RequestParam String token) {
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Upload session is valid",
                uploadSessionService.validate(sessionId, token));
    }

    @PostMapping("/upload-sessions/{sessionId}/presigned-url")
    public ResponseData<PresignedUploadResponseDTO> presign(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PresignUploadFileRequestDTO request) {
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Presigned upload URL created",
                uploadSessionService.presign(
                        sessionId,
                        request.token(),
                        request.fileName(),
                        request.contentType(),
                        request.size()));
    }

    @PostMapping("/upload-sessions/{sessionId}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseData<UploadSessionCompleteResponseDTO> complete(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CompleteUploadSessionRequestDTO request) {
        return new ResponseData<>(
                HttpStatus.ACCEPTED.value(),
                "Upload session accepted for extraction",
                uploadSessionService.complete(sessionId, request.token()));
    }
}
