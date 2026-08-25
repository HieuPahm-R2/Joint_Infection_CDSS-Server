package com.vietnam.pji.message;

import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConsumerNotificationLinkTest {

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
