package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExchangeUploadSessionRequestDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Invalid exchange code") String code) {
    @Override
    public String toString() {
        return "ExchangeUploadSessionRequestDTO[code=REDACTED]";
    }
}
