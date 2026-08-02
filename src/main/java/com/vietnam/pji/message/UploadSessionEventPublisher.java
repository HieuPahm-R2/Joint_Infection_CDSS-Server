package com.vietnam.pji.message;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.vietnam.pji.config.integration.RabbitMQConfig;
import com.vietnam.pji.dto.request.UploadSessionCleanupMessage;
import com.vietnam.pji.dto.request.UploadSessionQueueMessage;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadSessionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishCompleted(UploadSessionQueueMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_UPLOAD_SESSION_COMPLETED,
                message);
    }

    public void scheduleCleanup(UploadSessionCleanupMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_UPLOAD_SESSION_CLEANUP_DELAY,
                message);
    }
}
