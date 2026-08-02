package com.vietnam.pji.model.upload;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

@Builder
public record UploadSessionFile(
        UUID fileId,
        String originalName,
        String contentType,
        long declaredSize,
        String bucket,
        String objectKey,
        Instant createdAt) {
}
