package com.vietnam.pji.controller.agentic;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.agent.impl.ThinkingStreamRelay;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Sent Events relay for AI recommendation "thought logs" and "thinking stream".
 *
 * Clients open a stream on a runId and receive both macro progress events
 * and token-by-token reasoning (from Redis Streams).
 */
@RestController
@RequestMapping("${api.prefix}")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "AI Recommendation Stream", description = "Server-Sent Events stream of AI recommendation progress (thought logs)")
public class AiRecommendationStreamController {

    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000L; // 15 minutes to match max tail

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    /** RunIds that have a ThinkingStreamRelay actively tailing Redis Streams. */
    private final Set<Long> relayActiveRuns = ConcurrentHashMap.newKeySet();

    private final RecommendationAccessService recommendationAccessService;
    private final ThinkingStreamRelay thinkingStreamRelay;

    @GetMapping(value = "/ai-recommendations/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream AI recommendation progress and thinking via SSE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opened text/event-stream; emits progress events and thinking stream"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    public SseEmitter streamRun(
            @PathVariable Long runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) {
            
        recommendationAccessService.assertCanAccessRun(runId);
        // Stop reverse proxies (nginx / Cloudflare) buffering the event stream
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Replace any stale emitter for this runId
        SseEmitter previous = emitters.put(runId, emitter);
        if (previous != null) {
            try {
                previous.complete();
            } catch (Exception ignored) {
            }
        }

        emitter.onCompletion(() -> {
            emitters.remove(runId, emitter);
            relayActiveRuns.remove(runId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(runId, emitter);
            relayActiveRuns.remove(runId);
            emitter.complete();
        });
        emitter.onError(e -> {
            emitters.remove(runId, emitter);
            relayActiveRuns.remove(runId);
        });

        // Send an initial comment so the client sees the connection is live.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            log.debug("SSE initial send failed for runId={}: {}", runId, e.getMessage());
            emitter.completeWithError(e);
            return emitter;
        }

        log.info("SSE client connected for runId={}", runId);
        
        // Mark relay as active BEFORE dispatching, so closeRun() knows to defer.
        relayActiveRuns.add(runId);
        thinkingStreamRelay.relay(runId, lastEventId, emitter);
        
        return emitter;
    }

    /**
     * Push a legacy progress message to the SSE stream. (Still called by RabbitMQConsumer)
     */
    public void pushProgress(Long runId, String message, String stage) {
        SseEmitter emitter = emitters.get(runId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name(stage != null ? stage : "step")
                    .data(message != null ? message : ""));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE pushProgress failed for runId={}: {} — dropping emitter", runId, e.getMessage());
            emitters.remove(runId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Signal that the run is finished via RabbitMQ result.
     *
     * If a ThinkingStreamRelay is actively tailing this runId, we do NOT
     * complete the emitter here — the relay will see the terminal event
     * ("done"/"error"/"cancelled") in the Redis Stream and close the
     * emitter itself. This prevents a race condition where the RabbitMQ
     * result message arrives before the relay has forwarded all thinking
     * events to the client.
     */
    public void closeRun(Long runId, String finalMessage) {
        if (runId == null) return;

        if (relayActiveRuns.contains(runId)) {
            // Relay is running — it owns the emitter lifecycle.
            // Remove from map so pushProgress() stops writing.
            log.debug("closeRun for runId={}: relay is active, deferring emitter close to relay", runId);
            emitters.remove(runId);
            return;
        }

        // No relay running — close the emitter directly (legacy path).
        SseEmitter emitter = emitters.remove(runId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(finalMessage != null ? finalMessage : ""));
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE close failed for runId={}: {}", runId, e.getMessage());
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {}
        }
    }
}
