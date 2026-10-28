package com.vietnam.pji.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CreateUploadSessionResponseDTO(
        UUID sessionId,
        String qrPayload,
        Instant expiresAt,
        Instant exchangeCodeExpiresAt) {
    @Override
    public String toString() {
        return "CreateUploadSessionResponseDTO[sessionId=" + sessionId + ", qrPayload=REDACTED, expiresAt="
                + expiresAt + ", exchangeCodeExpiresAt=" + exchangeCodeExpiresAt + "]";
    }
}
