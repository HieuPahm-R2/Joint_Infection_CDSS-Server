package com.vietnam.pji.services.agent;

import com.vietnam.pji.services.agent.impl.AiRecommendationServiceImpl;
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
}

