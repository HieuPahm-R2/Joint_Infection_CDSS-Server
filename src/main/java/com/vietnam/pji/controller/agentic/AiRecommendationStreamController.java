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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Sent Events relay for AI recommendation "thought logs".
 *
 * Clients open a stream on a runId and receive progress messages forwarded
 * from RabbitMQ (published by the Python RAG worker). Nothing is persisted —
 * the controller just holds one in-memory emitter per runId and pushes events
 * onto it as they arrive.
 */
@RestController
@RequestMapping("${api.prefix}")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "AI Recommendation Stream", description = "Server-Sent Events stream of AI recommendation progress (thought logs)")
public class AiRecommendationStreamController {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L; // 10 minutes

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final RecommendationAccessService recommendationAccessService;

    @GetMapping(value = "/ai-recommendations/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream AI recommendation progress (thought logs) via SSE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opened text/event-stream; emits named progress events and a terminal 'done' event"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    public SseEmitter streamRun(@PathVariable Long runId, HttpServletResponse response) {
        recommendationAccessService.assertCanAccessRun(runId);
        // Stop reverse proxies (nginx / Cloudflare) buffering the event stream —
        // without this the thought-log frames arrive in one batch at the end
        // (or not at all) in deployment, even though they stream fine locally.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Replace any stale emitter for this runId (client reconnect after navigation).
        SseEmitter previous = emitters.put(runId, emitter);
        if (previous != null) {
            try {
                previous.complete();
            } catch (Exception ignored) {
                // Old emitter may already be dead — safe to drop.
            }
        }

        emitter.onCompletion(() -> emitters.remove(runId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(runId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(runId, emitter));

        // Send an initial comment so the client sees the connection is live.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            log.debug("SSE initial send failed for runId={}: {}", runId, e.getMessage());
            emitter.completeWithError(e);
        }

        log.debug("SSE client connected for runId={}", runId);
        return emitter;
    }

    /**
     * Push a progress message to the SSE stream for the given runId.
     * No-op if no client is currently listening.
     */
    public void pushProgress(Long runId, String message, String stage) {
        if (runId == null)
            return;
        SseEmitter emitter = emitters.get(runId);
        if (emitter == null)
            return;

        try {
            emitter.send(SseEmitter.event()
                    .name(stage != null ? stage : "step")
                    .data(message != null ? message : ""));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE push failed for runId={}: {} — dropping emitter", runId, e.getMessage());
            emitters.remove(runId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Send a terminal "done" event and close the stream for this runId.
     * Called by the result consumer once the run reaches SUCCESS/FAILED.
     */
    public void closeRun(Long runId, String finalMessage) {
        if (runId == null)
            return;
        SseEmitter emitter = emitters.remove(runId);
        if (emitter == null)
            return;

        try {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(finalMessage != null ? finalMessage : ""));
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE close failed for runId={}: {}", runId, e.getMessage());
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }
}
