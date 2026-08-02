package com.vietnam.pji.dto.response;

import java.util.List;
import java.util.UUID;

import com.vietnam.pji.constant.UploadSessionStatus;

public record UploadSessionEventDTO(
        UUID sessionId,
        UploadSessionStatus status,
        String jobId,
        List<UploadedImageDTO> images) {

    public record UploadedImageDTO(
            UUID fileId,
            String name,
            String contentType,
            long size,
            String previewUrl) {
    }
}
