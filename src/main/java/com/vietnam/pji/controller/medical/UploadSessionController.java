package com.vietnam.pji.controller.medical;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.vietnam.pji.dto.request.ExchangeUploadSessionRequestDTO;
import com.vietnam.pji.dto.request.CreateUploadSessionRequestDTO;
import com.vietnam.pji.dto.request.PresignUploadFileRequestDTO;
import com.vietnam.pji.dto.response.CreateUploadSessionResponseDTO;
import com.vietnam.pji.dto.response.PresignedUploadResponseDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.dto.response.UploadSessionCompleteResponseDTO;
import com.vietnam.pji.dto.response.UploadSessionValidationResponseDTO;
import com.vietnam.pji.dto.response.ExchangeUploadSessionResponseDTO;
import com.vietnam.pji.exception.UploadSessionUnauthorizedException;
import com.vietnam.pji.services.upload.UploadSessionService;
import com.vietnam.pji.utils.SecurityUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
@Tag(name = "Upload Sessions", description = "Short-lived QR upload sessions for clinical images")
public class UploadSessionController {

    private final UploadSessionService uploadSessionService;

    @ModelAttribute
    void preventCapabilityCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ResponseData<Void>> invalidBody() {
        return ResponseEntity.badRequest().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ResponseData<>(HttpStatus.BAD_REQUEST.value(), "A valid JSON request body is required", null));
    }

    @PostMapping("/upload-sessions/{sessionId}/exchange")
    public ResponseData<ExchangeUploadSessionResponseDTO> exchange(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ExchangeUploadSessionRequestDTO request) {
        return new ResponseData<>(HttpStatus.OK.value(), "Upload code exchanged",
                uploadSessionService.exchange(sessionId, request.code()));
    }

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
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Upload session is valid",
                uploadSessionService.validate(sessionId, capability(authorization)));
    }

    @PostMapping("/upload-sessions/{sessionId}/presigned-url")
    public ResponseData<PresignedUploadResponseDTO> presign(
            @PathVariable UUID sessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody PresignUploadFileRequestDTO request) {
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Presigned upload URL created",
                uploadSessionService.presign(
                        sessionId,
                        capability(authorization),
                        request.fileName(),
                        request.contentType(),
                        request.size()));
    }

    @PostMapping("/upload-sessions/{sessionId}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseData<UploadSessionCompleteResponseDTO> complete(
            @PathVariable UUID sessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return new ResponseData<>(
                HttpStatus.ACCEPTED.value(),
                "Upload session accepted for extraction",
                uploadSessionService.complete(sessionId, capability(authorization)));
    }

    private String capability(String authorization) {
        if (authorization == null || !authorization.matches("(?i)Bearer [A-Za-z0-9_-]{43}")) {
            throw new UploadSessionUnauthorizedException("Upload token is required in the Authorization header");
        }
        return authorization.substring(7);
    }
}
