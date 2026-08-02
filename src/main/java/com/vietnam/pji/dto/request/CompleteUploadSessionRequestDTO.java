package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompleteUploadSessionRequestDTO(
        @NotBlank(message = "token is required") String token) {
}
