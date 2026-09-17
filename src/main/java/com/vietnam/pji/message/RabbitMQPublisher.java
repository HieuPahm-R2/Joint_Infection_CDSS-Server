package com.vietnam.pji.message;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.vietnam.pji.config.integration.RabbitMQConfig;
import com.vietnam.pji.dto.request.RabbitMQRecommendationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RabbitMQPublisher {

        private final RabbitTemplate rabbitTemplate;
        private final long publishConfirmTimeoutMs;

        public RabbitMQPublisher(
                        RabbitTemplate rabbitTemplate,
                        @Value("${app.recommendation-outbox.publisher-confirm-timeout-ms:5000}")
                        long publishConfirmTimeoutMs) {
                this.rabbitTemplate = rabbitTemplate;
                this.publishConfirmTimeoutMs = Math.max(1, publishConfirmTimeoutMs);
        }

        public void publishRecommendationJob(RabbitMQRecommendationMessage message) {
                log.info("Publishing recommendation job to RabbitMQ: requestId={}, episodeId={}, triggerType={}",
                                message.getRequestId(), message.getEpisodeId(), message.getTriggerType());

                CorrelationData correlationData = new CorrelationData(message.getRequestId());
                rabbitTemplate.convertAndSend(
                                RabbitMQConfig.EXCHANGE,
                                RabbitMQConfig.ROUTING_KEY_GENERATE,
                                message,
                                correlationData);

                CorrelationData.Confirm confirm;
                try {
                        confirm = correlationData.getFuture().get(publishConfirmTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for recommendation publish confirm",
                                        exception);
                } catch (ExecutionException | TimeoutException exception) {
                        throw new IllegalStateException("Recommendation publish was not confirmed by RabbitMQ",
                                        exception);
                }

                if (!confirm.isAck()) {
                        throw new IllegalStateException(
                                        "RabbitMQ rejected recommendation publish: " + confirm.getReason());
                }
                if (correlationData.getReturned() != null) {
                        throw new IllegalStateException("RabbitMQ returned unroutable recommendation publish: "
                                        + correlationData.getReturned().getReplyText());
                }
        }

        public void publishRefreshJob(RabbitMQRecommendationMessage message) {
                log.info("Publishing refresh job to RabbitMQ: requestId={}, episodeId={}",
                                message.getRequestId(), message.getEpisodeId());

                rabbitTemplate.convertAndSend(
                                RabbitMQConfig.EXCHANGE,
                                RabbitMQConfig.ROUTING_KEY_REFRESH,
                                message);
        }
}
