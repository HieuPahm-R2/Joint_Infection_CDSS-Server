package com.vietnam.pji.repository.upload;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.UploadSessionStatus;
import com.vietnam.pji.model.upload.UploadSession;
import com.vietnam.pji.model.upload.UploadSessionFile;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RedisUploadSessionRepository implements UploadSessionRepository {

    private static final String KEY_PREFIX = "upload-session:";
    private static final String FILE_PREFIX = "file:";

    private static final DefaultRedisScript<Long> ADD_FILE_SCRIPT = script("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 2 end
            if redis.call('HGET', KEYS[1], 'tokenHash') ~= ARGV[1] then return 3 end
            if redis.call('HGET', KEYS[1], 'status') ~= 'PENDING' then return 4 end
            local fields = redis.call('HKEYS', KEYS[1])
            local count = 0
            for _, field in ipairs(fields) do
              if string.sub(field, 1, 5) == 'file:' then count = count + 1 end
            end
            if count >= tonumber(ARGV[4]) then return 5 end
            redis.call('HSET', KEYS[1], ARGV[2], ARGV[3])
            return 1
            """);

    private static final DefaultRedisScript<Long> MARK_UPLOADED_SCRIPT = script("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 2 end
            if redis.call('HGET', KEYS[1], 'tokenHash') ~= ARGV[1] then return 3 end
            if redis.call('HGET', KEYS[1], 'status') ~= 'PENDING' then return 4 end
            local fields = redis.call('HKEYS', KEYS[1])
            local count = 0
            for _, field in ipairs(fields) do
              if string.sub(field, 1, 5) == 'file:' then count = count + 1 end
            end
            if count == 0 then return 5 end
            redis.call('HSET', KEYS[1], 'status', 'UPLOADED', 'completedAt', ARGV[2])
            return 1
            """);

    private static final DefaultRedisScript<Long> CLAIM_PROCESSING_SCRIPT = script("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 2 end
            if redis.call('HGET', KEYS[1], 'status') ~= 'UPLOADED' then return 4 end
            if redis.call('HGET', KEYS[1], 'processing') == '1' then return 6 end
            redis.call('HSET', KEYS[1], 'processing', '1')
            return 1
            """);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void create(UploadSession session, Duration ttl) {
        String key = key(session.sessionId());
        Map<Object, Object> values = new HashMap<>();
        values.put("sessionId", session.sessionId().toString());
        values.put("tokenHash", session.tokenHash());
        values.put("patientId", session.patientId().toString());
        values.put("episodeId", session.episodeId().toString());
        values.put("doctorId", session.doctorId().toString());
        values.put("createdAt", session.createdAt().toString());
        values.put("expiresAt", session.expiresAt().toString());
        values.put("status", session.status().name());
        values.put("processing", "0");
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, ttl);
    }

    @Override
    public Optional<UploadSession> find(UUID sessionId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key(sessionId));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        List<UploadSessionFile> files = new ArrayList<>();
        values.forEach((field, value) -> {
            if (String.valueOf(field).startsWith(FILE_PREFIX)) {
                try {
                    files.add(objectMapper.readValue(String.valueOf(value), UploadSessionFile.class));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Corrupt upload-session file metadata", e);
                }
            }
        });
        files.sort((left, right) -> left.createdAt().compareTo(right.createdAt()));
        return Optional.of(UploadSession.builder()
                .sessionId(sessionId)
                .tokenHash(string(values, "tokenHash"))
                .patientId(longValue(values, "patientId"))
                .episodeId(longValue(values, "episodeId"))
                .doctorId(longValue(values, "doctorId"))
                .createdAt(instant(values, "createdAt"))
                .expiresAt(instant(values, "expiresAt"))
                .completedAt(instant(values, "completedAt"))
                .status(UploadSessionStatus.valueOf(string(values, "status")))
                .jobId(string(values, "jobId"))
                .processing("1".equals(string(values, "processing")))
                .files(List.copyOf(files))
                .build());
    }

    @Override
    public MutationResult addFile(UUID sessionId, String tokenHash, UploadSessionFile file, int maxFiles) {
        try {
            Long code = redisTemplate.execute(
                    ADD_FILE_SCRIPT,
                    List.of(key(sessionId)),
                    tokenHash,
                    FILE_PREFIX + file.fileId(),
                    objectMapper.writeValueAsString(file),
                    String.valueOf(maxFiles));
            return result(code);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize upload-session file metadata", e);
        }
    }

    @Override
    public MutationResult markUploaded(UUID sessionId, String tokenHash, Instant completedAt) {
        Long code = redisTemplate.execute(
                MARK_UPLOADED_SCRIPT,
                List.of(key(sessionId)),
                tokenHash,
                completedAt.toString());
        return result(code);
    }

    @Override
    public MutationResult claimProcessing(UUID sessionId) {
        return result(redisTemplate.execute(
                CLAIM_PROCESSING_SCRIPT,
                List.of(key(sessionId))));
    }

    @Override
    public void removeFile(UUID sessionId, UUID fileId) {
        redisTemplate.opsForHash().delete(key(sessionId), FILE_PREFIX + fileId);
    }

    @Override
    public void releaseProcessing(UUID sessionId) {
        redisTemplate.opsForHash().put(key(sessionId), "processing", "0");
    }

    @Override
    public void restorePending(UUID sessionId) {
        redisTemplate.opsForHash().put(key(sessionId), "status", UploadSessionStatus.PENDING.name());
        redisTemplate.opsForHash().delete(key(sessionId), "completedAt");
    }

    @Override
    public void markConsumed(UUID sessionId, String jobId) {
        redisTemplate.opsForHash().put(key(sessionId), "status", UploadSessionStatus.CONSUMED.name());
        redisTemplate.opsForHash().put(key(sessionId), "jobId", jobId);
        redisTemplate.opsForHash().put(key(sessionId), "processing", "0");
    }

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }

    private MutationResult result(Long code) {
        if (code == null) return MutationResult.NOT_FOUND;
        return switch (code.intValue()) {
            case 1 -> MutationResult.SUCCESS;
            case 2 -> MutationResult.NOT_FOUND;
            case 3 -> MutationResult.INVALID_TOKEN;
            case 4 -> MutationResult.INVALID_STATUS;
            case 5 -> MutationResult.LIMIT_REACHED;
            case 6 -> MutationResult.ALREADY_PROCESSING;
            default -> throw new IllegalStateException("Unknown Redis upload-session result: " + code);
        };
    }

    private String key(UUID sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private String string(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Map<Object, Object> values, String key) {
        String value = string(values, key);
        return value == null ? null : Long.valueOf(value);
    }

    private Instant instant(Map<Object, Object> values, String key) {
        String value = string(values, key);
        return value == null ? null : Instant.parse(value);
    }
}
