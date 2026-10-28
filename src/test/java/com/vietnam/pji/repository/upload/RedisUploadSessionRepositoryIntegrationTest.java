package com.vietnam.pji.repository.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.UploadSessionStatus;
import com.vietnam.pji.model.upload.UploadSession;
import com.vietnam.pji.repository.upload.UploadSessionRepository.MutationResult;

/** Run against an isolated Redis: QR_TEST_REDIS_PORT=<published loopback port>. */
@EnabledIfEnvironmentVariable(named = "QR_TEST_REDIS_PORT", matches = "\\d+")
class RedisUploadSessionRepositoryIntegrationTest {
    private JedisConnectionFactory connection;
    private RedisTemplate<String, Object> redis;
    private RedisUploadSessionRepository repository;
    private final List<UUID> ids = new ArrayList<>();

    @BeforeEach
    void setup() {
        connection = new JedisConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",
                Integer.parseInt(System.getenv("QR_TEST_REDIS_PORT"))));
        connection.afterPropertiesSet();
        connection.start();
        redis = new RedisTemplate<>();
        redis.setConnectionFactory(connection);
        var serializer = new StringRedisSerializer();
        redis.setKeySerializer(serializer);
        redis.setValueSerializer(serializer);
        redis.setHashKeySerializer(serializer);
        redis.setHashValueSerializer(serializer);
        redis.afterPropertiesSet();
        repository = new RedisUploadSessionRepository(redis, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void cleanup() {
        if (redis != null) ids.forEach(id -> redis.delete("upload-session:" + id));
        if (connection != null) connection.destroy();
    }

    @Test
    void concurrentExchangesHaveExactlyOneWinnerAndDoNotExtendTtl() throws Exception {
        UUID id = create(Instant.now().plusSeconds(30));
        Long ttlBefore = redis.getExpire("upload-session:" + id, TimeUnit.MILLISECONDS);
        var executor = Executors.newFixedThreadPool(8);
        var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<MutationResult>>();
            for (int i = 0; i < 16; i++) {
                final String tokenHash = "upload-hash-" + i;
                futures.add(executor.submit(() -> { gate.await(); return repository.exchangeCode(id, "code-hash", tokenHash); }));
            }
            gate.countDown();
            var results = new ArrayList<MutationResult>();
            for (var future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(result -> result == MutationResult.SUCCESS).count()).isEqualTo(1);
            assertThat(results.stream().filter(result -> result == MutationResult.INVALID_TOKEN).count()).isEqualTo(15);
            UploadSession exchanged = repository.find(id).orElseThrow();
            assertThat(exchanged.tokenHash()).startsWith("upload-hash-");
            assertThat(exchanged.exchangeCodeHash()).isNull();
            assertThat(exchanged.exchangeCodeExpiresAt()).isNull();
            assertThat(redis.getExpire("upload-session:" + id, TimeUnit.MILLISECONDS)).isPositive().isLessThanOrEqualTo(ttlBefore);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void badCodeDoesNotConsumeValidCodeAndReplayCannotReplaceIssuedToken() {
        UUID id = create(Instant.now().plusSeconds(30));
        assertThat(repository.exchangeCode(id, "wrong-hash", "attacker-hash")).isEqualTo(MutationResult.INVALID_TOKEN);
        UUID other = create(Instant.now().plusSeconds(30));
        redis.opsForHash().put("upload-session:" + other, "exchangeCodeHash", "other-code-hash");
        assertThat(repository.exchangeCode(other, "code-hash", "attacker-hash")).isEqualTo(MutationResult.INVALID_TOKEN);
        assertThat(repository.exchangeCode(other, "other-code-hash", "other-token-hash")).isEqualTo(MutationResult.SUCCESS);
        assertThat(repository.exchangeCode(UUID.randomUUID(), "code-hash", "attacker-hash")).isEqualTo(MutationResult.NOT_FOUND);
        assertThat(repository.exchangeCode(id, "code-hash", "issued-hash")).isEqualTo(MutationResult.SUCCESS);
        assertThat(repository.exchangeCode(id, "code-hash", "replacement-hash")).isEqualTo(MutationResult.INVALID_TOKEN);
        assertThat(repository.find(id).orElseThrow().tokenHash()).isEqualTo("issued-hash");
    }

    @Test
    void expiredCodeNonPendingAndMissingSessionCannotExchange() {
        UUID expired = create(Instant.now().minusSeconds(1));
        assertThat(repository.exchangeCode(expired, "code-hash", "token-hash")).isEqualTo(MutationResult.INVALID_STATUS);
        assertThat(repository.find(expired).orElseThrow().tokenHash()).isNull();
        UUID consumed = create(Instant.now().plusSeconds(30));
        redis.opsForHash().put("upload-session:" + consumed, "status", "CONSUMED");
        assertThat(repository.exchangeCode(consumed, "code-hash", "token-hash")).isEqualTo(MutationResult.INVALID_STATUS);
        UUID missing = create(Instant.now().plusSeconds(30));
        redis.delete("upload-session:" + missing);
        assertThat(repository.exchangeCode(missing, "code-hash", "token-hash")).isEqualTo(MutationResult.NOT_FOUND);
        UUID noTtl = create(Instant.now().plusSeconds(30));
        redis.persist("upload-session:" + noTtl);
        assertThat(repository.exchangeCode(noTtl, "code-hash", "token-hash")).isEqualTo(MutationResult.INVALID_STATUS);
    }

    private UUID create(Instant codeExpiresAt) {
        UUID id = UUID.randomUUID();
        ids.add(id);
        repository.create(UploadSession.builder().sessionId(id).patientId(7L).episodeId(23L).doctorId(99L)
                .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(240)).status(UploadSessionStatus.PENDING)
                .exchangeCodeHash("code-hash").exchangeCodeExpiresAt(codeExpiresAt).files(List.of()).build(), Duration.ofSeconds(240));
        return id;
    }
}
