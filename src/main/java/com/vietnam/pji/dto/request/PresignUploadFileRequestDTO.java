package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PresignUploadFileRequestDTO(
        @NotBlank(message = "token is required") String token,
        @NotBlank(message = "fileName is required") String fileName,
        @NotBlank(message = "contentType is required") String contentType,
        @Positive(message = "size must be positive") long size) {
}
