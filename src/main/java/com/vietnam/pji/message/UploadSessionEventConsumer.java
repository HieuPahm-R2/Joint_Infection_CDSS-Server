package com.vietnam.pji.message;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.vietnam.pji.config.integration.RabbitMQConfig;
import com.vietnam.pji.controller.medical.UploadSessionStreamController;
import com.vietnam.pji.dto.request.UploadSessionCleanupMessage;
import com.vietnam.pji.dto.request.UploadSessionQueueMessage;
import com.vietnam.pji.services.upload.UploadSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UploadSessionEventConsumer {

    private final UploadSessionService uploadSessionService;
    private final UploadSessionStreamController streamController;

    @RabbitListener(queues = RabbitMQConfig.UPLOAD_SESSION_QUEUE)
    public void handleCompleted(UploadSessionQueueMessage message) {
        if (message == null || message.sessionId() == null) {
            log.warn("Dropping upload-session message without sessionId");
            return;
        }
        uploadSessionService.processCompletedSession(message.sessionId())
                .ifPresent(streamController::push);
    }

    @RabbitListener(queues = RabbitMQConfig.UPLOAD_SESSION_CLEANUP_QUEUE)
    public void handleCleanup(UploadSessionCleanupMessage message) {
        uploadSessionService.cleanup(message);
    }
}
