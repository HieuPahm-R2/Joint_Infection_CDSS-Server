package com.vietnam.pji.dto.request;

import java.util.List;
import java.util.UUID;

public record UploadSessionCleanupMessage(
        UUID sessionId,
        List<ObjectRef> objects) {

    public record ObjectRef(String bucket, String objectKey) {
    }
}
