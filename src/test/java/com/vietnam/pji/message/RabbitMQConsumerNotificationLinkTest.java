package com.vietnam.pji.message;

import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConsumerNotificationLinkTest {

    @Test
    void treatsEveryCompletedOutcomeAsIdempotent() {
        assertThat(RabbitMQConsumer.isTerminalResultStatus(RunStatus.SUCCESS)).isTrue();
        assertThat(RabbitMQConsumer.isTerminalResultStatus(RunStatus.PARTIAL)).isTrue();
        assertThat(RabbitMQConsumer.isTerminalResultStatus(RunStatus.FAILED)).isTrue();
        assertThat(RabbitMQConsumer.isTerminalResultStatus(RunStatus.TIMEOUT)).isTrue();
        assertThat(RabbitMQConsumer.isTerminalResultStatus(RunStatus.CANCELLED)).isTrue();
        assertThat(RabbitMQConsumer.isTerminalResultStatus(RunStatus.PROCESSING)).isFalse();
        assertThat(RabbitMQConsumer.isTerminalResultStatus(RunStatus.QUEUED)).isFalse();
    }

    @Test
    void routesSurgeryRunsToDoctorWorkflow() {
        AiRecommendationRun run = AiRecommendationRun.builder()
                .recommendationScope(RecommendationScope.SURGERY)
                .build();
        run.setId(11L);

        assertThat(RabbitMQConsumer.buildRecommendationLink(run, 22L))
                .isEqualTo("/?runId=11&episodeId=22");
    }

    @Test
    void routesAntibioticRunsToPharmacistWorkflow() {
        AiRecommendationRun run = AiRecommendationRun.builder()
                .recommendationScope(RecommendationScope.ANTIBIOTIC)
                .build();
        run.setId(11L);

        assertThat(RabbitMQConsumer.buildRecommendationLink(run, 22L))
                .isEqualTo("/antibiotic-planner?runId=11&episodeId=22");
    }
}
