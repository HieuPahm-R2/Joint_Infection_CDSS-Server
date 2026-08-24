package com.vietnam.pji.services.agent;

import com.vietnam.pji.constant.TriggerType;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.dto.response.AiRecommendationRunDetailDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.services.diagnosis.PjiDiagnosticRuleEngine;
import org.springframework.data.domain.Pageable;

public interface AiRecommendationService {

    /** Synchronous: calls AI service via HTTP, blocks until result. */
    AiRecommendationRunDetailDTO generateRecommendation(Long episodeId, TriggerType triggerType,
            RecommendationScope recommendationScope);

    /** Async: publishes to RabbitMQ, returns immediately with PROCESSING run. */
    AiRecommendationRunDetailDTO generateRecommendationAsync(Long episodeId, TriggerType triggerType,
            RecommendationScope recommendationScope);

    /** Rule-based diagnostic only; does not call RAG/AI and does not create an AI run. */
    PjiDiagnosticRuleEngine.DiagnosticResult evaluateRuleBasedDiagnostic(Long episodeId);

    AiRecommendationRunDetailDTO getRunDetail(Long runId);

    PaginationResultDTO getRunHistory(Long episodeId, Pageable pageable);

    AiRecommendationRunDetailDTO retryRun(Long runId);

    /**
     * Cancel an in-flight run. Validates ownership (caller must be the user who
     * started the run), checks the run is still in a cancellable state, marks
     * the row as CANCELLED, and writes a Redis cancel flag so the Python worker
     * sees it on its next checkpoint.
     */
    void cancelRun(Long runId);
}
