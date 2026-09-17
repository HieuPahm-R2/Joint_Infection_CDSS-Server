package com.vietnam.pji.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecommendationJobOutboxScheduler {

    private final RecommendationJobOutboxDispatcher dispatcher;
    private final int batchSize;

    public RecommendationJobOutboxScheduler(
            RecommendationJobOutboxDispatcher dispatcher,
            @Value("${app.recommendation-outbox.batch-size:10}") int batchSize) {
        this.dispatcher = dispatcher;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${app.recommendation-outbox.poll-delay-ms:1000}")
    public void dispatchReadyJobs() {
        for (int dispatched = 0; dispatched < batchSize; dispatched++) {
            if (!dispatcher.dispatchNext()) {
                return;
            }
        }
    }
}
