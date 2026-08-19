package com.vietnam.pji.config.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool for @Async methods like ThinkingStreamRelay.
 * Each SSE relay occupies a thread for the duration of the stream,
 * so we keep the pool bounded to prevent thread exhaustion.
 */
@Configuration
public class AsyncConfig {

    @Bean("thinkingRelayExecutor")
    public Executor thinkingRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("thinking-relay-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}
