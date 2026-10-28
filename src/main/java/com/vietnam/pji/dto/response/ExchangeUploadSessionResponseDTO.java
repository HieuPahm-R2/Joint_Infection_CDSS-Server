package com.vietnam.pji.dto.response;

import java.time.Instant;

public record ExchangeUploadSessionResponseDTO(String uploadToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "ExchangeUploadSessionResponseDTO[uploadToken=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
