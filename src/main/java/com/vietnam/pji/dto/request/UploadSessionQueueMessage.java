package com.vietnam.pji.dto.request;

import java.time.Instant;
import java.util.UUID;

public record UploadSessionQueueMessage(
        UUID sessionId,
        Instant completedAt) {
}
