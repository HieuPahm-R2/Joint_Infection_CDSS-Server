package com.vietnam.pji.controller.notification;

import com.vietnam.pji.dto.response.NotificationResponseDTO;
import com.vietnam.pji.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-user Server-Sent Events stream for notifications. A user may have many
 * concurrent emitters (one per browser tab); each
 * {@link NotificationServiceImpl}
 * push fans out to all of them.
 */
@RestController
@RequestMapping("${api.prefix}")
@Slf4j
@Tag(name = "Notification Stream", description = "Server-Sent Events stream of per-user notifications")
public class NotificationStreamController {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L; // 30 minutes

    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();// userId 10 → [emitter tab A, emitter tab B]

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream notifications for the authenticated user via SSE")
    public ResponseEntity<SseEmitter> stream() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        //nếu user chưa có ds thì tạo; nếu đã có thì lấy ds hiện tại
        List<SseEmitter> list = emittersByUser.computeIfAbsent(userId,
                k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        //xóa emitter. Nếu user ko còn emitter nào thì xóa entry của user trong map.
        Runnable cleanup = () -> {
            List<SseEmitter> current = emittersByUser.get(userId);
            if (current != null) {
                current.remove(emitter);
                if (current.isEmpty()) {
                    emittersByUser.remove(userId, current);
                }
            }
        };
        // tình huống 1 -> kết nối oke -> dọn dẹp
        emitter.onCompletion(cleanup);
        // tình huống 2 -> hết timeout -> dọn dẹp
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            log.debug("SSE initial send failed for userId={}: {}", userId, e.getMessage());
            emitter.completeWithError(e);
        }

        log.debug("Notification SSE client connected for userId={}", userId);
        // yêu cầu proxy ko buffer, ko cache khi chưa xác thực lại
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache")
                .body(emitter);
    }

    /**
     * Push a noti to every active emitter for the given user. No-op if
     * the user has no open connections (it's still persisted to the DB by the
     * service caller).
     */
    public void push(Long userId, NotificationResponseDTO dto) {
        if (userId == null || dto == null) {
            return;
        }
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(dto, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                log.debug("Notification SSE push failed for userId={}: {} — dropping emitter",
                        userId, e.getMessage());
                list.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // emitter may already be terminal — fine.
                }
            }
        }
        if (list.isEmpty()) {
            emittersByUser.remove(userId, list);
        }
    }
}
