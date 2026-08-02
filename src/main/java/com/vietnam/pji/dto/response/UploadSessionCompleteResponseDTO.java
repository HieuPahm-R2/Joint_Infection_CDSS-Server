package com.vietnam.pji.dto.response;

import java.util.UUID;

import com.vietnam.pji.constant.UploadSessionStatus;

public record UploadSessionCompleteResponseDTO(
        UUID sessionId,
        UploadSessionStatus status,
        int fileCount) {
}
