package com.vietnam.pji.services.agent;

import com.vietnam.pji.dto.response.AiRecommendationGenerateResponseDTO;
import com.vietnam.pji.services.agent.impl.AiRecommendationServiceImpl;
import com.vietnam.pji.services.agent.impl.RecommendationResultWriter;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class AiRecommendationServiceTransactionContractTest {

    @Test
    void getRunDetailDoesNotDeclareItsCacheWriteTransactionReadOnly() throws NoSuchMethodException {
        Transactional transaction = AiRecommendationServiceImpl.class
                .getMethod("getRunDetail", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }

    /**
     * The AI result write must be reached across a bean boundary. While it lived on
     * {@link AiRecommendationServiceImpl} as a {@code @Transactional} method called
     * through {@code this.}, Spring's advice never applied and the run, its items
     * and its citations were persisted non-atomically.
     */
    @Test
    void aiResultWriteIsTransactionalOnItsOwnBean() throws NoSuchMethodException {
        Transactional transaction = RecommendationResultWriter.class
                .getMethod("save", Long.class, AiRecommendationGenerateResponseDTO.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
        assertThat(AiRecommendationServiceImpl.class.getDeclaredMethods())
                .as("the result write must not be declared on the non-transactional service")
                .noneMatch(method -> method.getName().equals("saveAiResults"));
    }
}
