package com.vietnam.pji.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UploadSessionValidationResponseDTO(
        UUID sessionId,
        Instant expiresAt,
        int maxFiles,
        long maxFileSizeBytes,
        Set<String> allowedContentTypes) {
}
