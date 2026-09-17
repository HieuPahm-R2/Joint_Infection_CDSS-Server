package com.vietnam.pji.message;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.dto.request.RabbitMQRecommendationMessage;
import com.vietnam.pji.model.agentic.RecommendationJobOutbox;
import com.vietnam.pji.repository.ai.RecommendationJobOutboxRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RecommendationJobOutboxDispatcher {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final RecommendationJobOutboxRepository outboxRepository;
    private final RabbitMQPublisher rabbitMQPublisher;
    private final ObjectMapper objectMapper;
    private final long maxBackoffSeconds;

    public RecommendationJobOutboxDispatcher(
            RecommendationJobOutboxRepository outboxRepository,
            RabbitMQPublisher rabbitMQPublisher,
            ObjectMapper objectMapper,
            @Value("${app.recommendation-outbox.max-backoff-seconds:60}") long maxBackoffSeconds) {
        this.outboxRepository = outboxRepository;
        this.rabbitMQPublisher = rabbitMQPublisher;
        this.objectMapper = objectMapper;
        this.maxBackoffSeconds = Math.max(1, maxBackoffSeconds);
    }

    /**
     * Dispatches one row while holding its database lock. Multiple application
     * replicas can safely poll because the repository uses FOR UPDATE SKIP LOCKED.
     * A crash after broker acknowledgement but before commit can redeliver the job,
     * so consumers must remain idempotent by runId/requestId.
     *
     * @return {@code true} when a ready row was handled, even if it was deferred
     *         for retry; {@code false} when no row was ready
     */
    @Transactional
    public boolean dispatchNext() {
        RecommendationJobOutbox outbox = outboxRepository.findNextReadyForUpdate().orElse(null);
        if (outbox == null) {
            return false;
        }

        if (outbox.getRun().getStatus() == RunStatus.CANCELLED) {
            log.info("Discarding recommendation outbox job for cancelled runId={}", outbox.getRun().getId());
            outboxRepository.delete(outbox);
            return true;
        }

        try {
            RabbitMQRecommendationMessage message = objectMapper.readValue(
                    outbox.getPayloadJson(), RabbitMQRecommendationMessage.class);
            rabbitMQPublisher.publishRecommendationJob(message);
            outboxRepository.delete(outbox);
            log.info("Dispatched recommendation outbox job: outboxId={}, runId={}, requestId={}",
                    outbox.getId(), outbox.getRun().getId(), message.getRequestId());
        } catch (Exception exception) {
            int attemptCount = outbox.getAttemptCount() + 1;
            long backoffSeconds = Math.min(maxBackoffSeconds, exponentialBackoffSeconds(attemptCount));
            outbox.setAttemptCount(attemptCount);
            outbox.setAvailableAt(Instant.now().plusSeconds(backoffSeconds));
            outbox.setLastError(truncateError(exception));
            log.warn("Recommendation outbox publish failed; retry scheduled: outboxId={}, runId={}, "
                    + "attempt={}, backoffSeconds={}",
                    outbox.getId(), outbox.getRun().getId(), attemptCount, backoffSeconds, exception);
        }

        return true;
    }

    private long exponentialBackoffSeconds(int attemptCount) {
        int exponent = Math.min(Math.max(0, attemptCount - 1), 30);
        return 1L << exponent;
    }

    private String truncateError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
    }
}
