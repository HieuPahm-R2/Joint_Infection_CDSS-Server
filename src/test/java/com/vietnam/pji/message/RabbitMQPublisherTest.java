package com.vietnam.pji.message;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.vietnam.pji.config.integration.RabbitMQConfig;
import com.vietnam.pji.dto.request.RabbitMQRecommendationMessage;

class RabbitMQPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitMQPublisher publisher = new RabbitMQPublisher(rabbitTemplate, 100);

    @Test
    void acceptsBrokerAcknowledgement() {
        completePublishWith(new CorrelationData.Confirm(true, null));

        assertThatCode(() -> publisher.publishRecommendationJob(message()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBrokerNegativeAcknowledgement() {
        completePublishWith(new CorrelationData.Confirm(false, "exchange rejected publish"));

        assertThatThrownBy(() -> publisher.publishRecommendationJob(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exchange rejected publish");
    }

    private void completePublishWith(CorrelationData.Confirm confirm) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(confirm);
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_GENERATE),
                any(RabbitMQRecommendationMessage.class),
                any(CorrelationData.class));
    }

    private RabbitMQRecommendationMessage message() {
        return RabbitMQRecommendationMessage.builder()
                .requestId("request-1")
                .runId(12L)
                .episodeId(7L)
                .build();
    }
}
