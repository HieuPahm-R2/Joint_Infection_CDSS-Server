package com.vietnam.pji.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CreateUploadSessionResponseDTO(
        UUID sessionId,
        String qrPayload,
        Instant expiresAt) {
}
