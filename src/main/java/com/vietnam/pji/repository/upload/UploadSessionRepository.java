package com.vietnam.pji.repository.upload;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.vietnam.pji.model.upload.UploadSession;
import com.vietnam.pji.model.upload.UploadSessionFile;

public interface UploadSessionRepository {

    enum MutationResult {
        SUCCESS,
        NOT_FOUND,
        INVALID_TOKEN,
        INVALID_STATUS,
        LIMIT_REACHED,
        ALREADY_PROCESSING
    }

    void create(UploadSession session, Duration ttl);

    Optional<UploadSession> find(UUID sessionId);

    MutationResult addFile(UUID sessionId, String tokenHash, UploadSessionFile file, int maxFiles);

    MutationResult markUploaded(UUID sessionId, String tokenHash, Instant completedAt);

    MutationResult claimProcessing(UUID sessionId);

    void removeFile(UUID sessionId, UUID fileId);

    void releaseProcessing(UUID sessionId);

    void restorePending(UUID sessionId);

    void markConsumed(UUID sessionId, String jobId);
}
