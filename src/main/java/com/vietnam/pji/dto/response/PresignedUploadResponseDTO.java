package com.vietnam.pji.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PresignedUploadResponseDTO(
        UUID fileId,
        String uploadUrl,
        String method,
        String contentType,
        Instant expiresAt) {
}
