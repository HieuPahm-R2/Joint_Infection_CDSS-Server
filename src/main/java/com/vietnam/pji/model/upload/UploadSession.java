package com.vietnam.pji.model.upload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vietnam.pji.constant.UploadSessionStatus;

import lombok.Builder;

@Builder(toBuilder = true)
public record UploadSession(
        UUID sessionId,
        String tokenHash,
        String exchangeCodeHash,
        Instant exchangeCodeExpiresAt,
        Long patientId,
        Long episodeId,
        Long doctorId,
        Instant createdAt,
        Instant expiresAt,
        Instant completedAt,
        UploadSessionStatus status,
        String jobId,
        boolean processing,
        List<UploadSessionFile> files) {
}
