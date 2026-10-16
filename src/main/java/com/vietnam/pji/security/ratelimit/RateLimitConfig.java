package com.vietnam.pji.security.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Slf4j
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:60000}")
    private int redisTimeoutMs;

    // The JedisPool is intentionally NOT exposed as a Spring bean: Spring's
    // MBeanExporter would try to auto-register it as an MBean (its toString()
    // matches the GenericObjectPool MBean signature) and clash with the cache
    // pool's already-registered MBean. Holding it as a private field and
    // closing it via @PreDestroy avoids that path entirely.
    private JedisPool jedisPool;

    @Bean
    public ProxyManager<String> rateLimitProxyManager() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestWhileIdle(true);
        // Disable Commons Pool's own JMX registration so it can't clash with
        // the cache pool's "pool" MXBean either.
        poolConfig.setJmxEnabled(false);

        String password = (redisPassword == null || redisPassword.isBlank()) ? null : redisPassword;
        log.info("Rate-limit Jedis pool: {}:{} db={}", redisHost, redisPort, redisDatabase);
        this.jedisPool = new JedisPool(
                poolConfig,
                redisHost,
                redisPort,
                redisTimeoutMs,
                password,
                redisDatabase);
        // Tạo ProxyManager<String> của Bucket4j trên Redis. Bộ đếm vì thế có thể được
        // chia sẻ giữa nhiều instance backend.
        return JedisBasedProxyManager.builderFor(jedisPool)
                .withKeyMapper(Mapper.STRING)
                .build();
    }

    @PreDestroy
    public void closePool() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
