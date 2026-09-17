package com.vietnam.pji.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.dto.request.RabbitMQRecommendationMessage;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.RecommendationJobOutbox;
import com.vietnam.pji.repository.ai.RecommendationJobOutboxRepository;

class RecommendationJobOutboxDispatcherTest {

    private final RecommendationJobOutboxRepository outboxRepository =
            mock(RecommendationJobOutboxRepository.class);
    private final RabbitMQPublisher publisher = mock(RabbitMQPublisher.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecommendationJobOutboxDispatcher dispatcher =
            new RecommendationJobOutboxDispatcher(outboxRepository, publisher, objectMapper, 60);

    @Test
    void deletesJobOnlyAfterConfirmedPublish() throws Exception {
        RecommendationJobOutbox outbox = outbox(RunStatus.PROCESSING);
        when(outboxRepository.findNextReadyForUpdate()).thenReturn(Optional.of(outbox));

        assertThat(dispatcher.dispatchNext()).isTrue();

        verify(publisher).publishRecommendationJob(any(RabbitMQRecommendationMessage.class));
        verify(outboxRepository).delete(outbox);
        assertThat(outbox.getAttemptCount()).isZero();
    }

    @Test
    void retainsJobAndSchedulesRetryWhenPublishFails() throws Exception {
        RecommendationJobOutbox outbox = outbox(RunStatus.PROCESSING);
        when(outboxRepository.findNextReadyForUpdate()).thenReturn(Optional.of(outbox));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publishRecommendationJob(any());
        Instant beforeDispatch = Instant.now();

        assertThat(dispatcher.dispatchNext()).isTrue();

        verify(outboxRepository, never()).delete(any());
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getAvailableAt()).isAfterOrEqualTo(beforeDispatch.plusSeconds(1));
        assertThat(outbox.getLastError()).isEqualTo("broker unavailable");
    }

    @Test
    void discardsPendingJobForCancelledRun() throws Exception {
        RecommendationJobOutbox outbox = outbox(RunStatus.CANCELLED);
        when(outboxRepository.findNextReadyForUpdate()).thenReturn(Optional.of(outbox));

        assertThat(dispatcher.dispatchNext()).isTrue();

        verify(outboxRepository).delete(outbox);
        verifyNoInteractions(publisher);
    }

    private RecommendationJobOutbox outbox(RunStatus status) throws Exception {
        AiRecommendationRun run = AiRecommendationRun.builder()
                .status(status)
                .requestId("request-1")
                .build();
        run.setId(12L);
        RabbitMQRecommendationMessage message = RabbitMQRecommendationMessage.builder()
                .requestId("request-1")
                .runId(12L)
                .episodeId(7L)
                .snapshotId(8L)
                .build();
        RecommendationJobOutbox outbox = RecommendationJobOutbox.builder()
                .run(run)
                .payloadJson(objectMapper.writeValueAsString(message))
                .availableAt(Instant.now())
                .build();
        outbox.setId(20L);
        return outbox;
    }
}
