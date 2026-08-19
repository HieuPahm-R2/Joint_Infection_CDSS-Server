package com.vietnam.pji.services.agent.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThinkingStreamRelay {

    private static final String KEY_PREFIX = "run:thinking:";
    private static final Set<String> TERMINAL = Set.of("done", "error", "cancelled");
    private static final Duration BLOCK = Duration.ofSeconds(1);
    private static final Duration MAX_TAIL = Duration.ofMinutes(15);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Tail the Redis Stream for a runId and push events directly to the client's SseEmitter.
     * Runs asynchronously on a separate thread pool.
     */
    @Async("thinkingRelayExecutor")
    public void relay(Long runId, String lastEventId, SseEmitter emitter) {
        String key = KEY_PREFIX + runId;
        String lastId = (lastEventId != null && !lastEventId.trim().isEmpty()) ? lastEventId : "0-0";
        Instant deadline = Instant.now().plus(MAX_TAIL);

        log.info("Starting ThinkingStreamRelay for runId={} from lastId={}", runId, lastId);

        try {
            while (Instant.now().isBefore(deadline)) {
                try {
                    @SuppressWarnings("unchecked")
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                            StreamReadOptions.empty().block(BLOCK).count(50),
                            StreamOffset.create(key, ReadOffset.from(lastId))
                    );

                    if (records == null || records.isEmpty()) {
                        continue; // block timed out, no new data
                    }

                    for (MapRecord<String, Object, Object> record : records) {
                        lastId = record.getId().getValue();
                        Map<Object, Object> rawFields = record.getValue();

                        // Convert to clean Map<String, String> for reliable JSON serialization
                        Map<String, String> fields = new LinkedHashMap<>();
                        for (Map.Entry<Object, Object> entry : rawFields.entrySet()) {
                            fields.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                        }

                        String type = fields.getOrDefault("type", "");
                        log.debug("Relaying event type={} for runId={}, streamId={}", type, runId, lastId);

                        try {
                            emitter.send(SseEmitter.event()
                                    .id(lastId)
                                    .name("thinking")
                                    .data(fields));
                        } catch (IOException | IllegalStateException e) {
                            log.debug("SSE emitter disconnected for runId={}: {} — stopping relay", runId, e.getMessage());
                            return; // Client disconnected -> exit thread immediately
                        }

                        if (TERMINAL.contains(type)) {
                            log.info("Terminal event '{}' received for runId={}, stopping relay", type, runId);
                            return; // Stream finished -> exit cleanly
                        }
                    }
                } catch (Exception e) {
                    log.error("Error tailing Redis stream for runId={}: {}", runId, e.getMessage(), e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            log.warn("ThinkingStreamRelay timed out (MAX_TAIL reached) for runId={}", runId);
        } finally {
            // Relay owns the emitter lifecycle — always complete it when done.
            try {
                emitter.complete();
            } catch (Exception ignored) {}
            log.debug("Relay ended and emitter completed for runId={}", runId);
        }
    }
}
