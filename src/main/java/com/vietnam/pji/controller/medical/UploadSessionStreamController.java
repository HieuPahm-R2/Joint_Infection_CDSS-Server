package com.vietnam.pji.controller.medical;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vietnam.pji.dto.response.UploadSessionEventDTO;
import com.vietnam.pji.services.upload.UploadSessionService;
import com.vietnam.pji.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
@Slf4j
public class UploadSessionStreamController {

    private static final long SSE_TIMEOUT_MS = 5L * 60L * 1000L;

    private final UploadSessionService uploadSessionService;
    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/upload-sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable UUID sessionId) {
        Long doctorId = SecurityUtils.getCurrentUserId();
        if (doctorId == null) {
            return ResponseEntity.status(401).build();
        }
        if (!uploadSessionService.exists(sessionId)) {
            return ResponseEntity.status(410).build();
        }
        if (!uploadSessionService.belongsToDoctor(sessionId, doctorId)) {
            return ResponseEntity.status(403).build();
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<SseEmitter> sessionEmitters = emitters.computeIfAbsent(
                sessionId,
                ignored -> new CopyOnWriteArrayList<>());
        sessionEmitters.add(emitter);
        Runnable cleanup = () -> remove(sessionId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onError(ignored -> cleanup.run());
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });

        try {
            emitter.send(SseEmitter.event().comment("connected"));
            uploadSessionService.replayEventForDoctor(sessionId, doctorId)
                    .ifPresent(event -> sendAndClose(sessionId, emitter, event));
        } catch (IOException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
        }

        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache")
                .body(emitter);
    }

    public void push(UploadSessionEventDTO event) {
        if (event == null || event.sessionId() == null) return;
        List<SseEmitter> active = emitters.remove(event.sessionId());
        if (active == null) return;
        for (SseEmitter emitter : active) {
            sendAndClose(event.sessionId(), emitter, event);
        }
    }

    private void sendAndClose(UUID sessionId, SseEmitter emitter, UploadSessionEventDTO event) {
        try {
            emitter.send(SseEmitter.event()
                    .name("uploaded")
                    .data(event, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException | IllegalStateException ex) {
            log.debug("Upload-session SSE push failed sessionId={}: {}", sessionId, ex.getMessage());
            try {
                emitter.completeWithError(ex);
            } catch (Exception ignored) {
            }
        } finally {
            remove(sessionId, emitter);
        }
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        List<SseEmitter> current = emitters.get(sessionId);
        if (current == null) return;
        current.remove(emitter);
        if (current.isEmpty()) emitters.remove(sessionId, current);
    }
}
