package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateUploadSessionRequestDTO(
        @NotNull(message = "episodeId is required") Long episodeId) {
}
