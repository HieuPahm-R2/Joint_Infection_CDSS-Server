package com.vietnam.pji.services.episode.impl;

import com.vietnam.pji.dto.response.EpisodeLockResponseDTO;
import com.vietnam.pji.exception.ResourceBusyException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.services.episode.EpisodeLockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EpisodeLockServiceImpl implements EpisodeLockService {

    static final String LOCK_KEY_PREFIX = "episode:lock:";
    static final Duration LOCK_TTL = Duration.ofMinutes(3);

    /**
     * Atomic check-and-extend: PEXPIRE only if the current value equals our holder.
     * Returns the new TTL in ms, or -1 if the lock is missing or owned by someone
     * else.
     */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                    "  redis.call('PEXPIRE', KEYS[1], ARGV[2]); " +
                    "  return tonumber(ARGV[2]); " +
                    "else return -1 end",
            Long.class);

    /**
     * Atomic check-and-delete: DEL only if the current value equals our holder.
     * Returns 1 on release, 0 if the caller is not the holder.
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('DEL', KEYS[1]); " +
                    "else return 0 end",
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final EpisodeRepository episodeRepository;

    @Override
    public EpisodeLockResponseDTO acquire(Long episodeId, Long userId) {
        requireEpisodeExists(episodeId);
        String key = lockKey(episodeId);
        String holder = String.valueOf(userId);

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, holder, LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            log.info("Episode {} locked by user {} (ttl={}s)", episodeId, userId, LOCK_TTL.toSeconds());
            return buildResponse(episodeId, userId, LOCK_TTL.toSeconds());
        }

        // Lock already exists — check whether the same user is re-acquiring.
        String currentHolder = redisTemplate.opsForValue().get(key);
        if (holder.equals(currentHolder)) {
            // Same doctor reopening the editor — refresh the TTL.
            return renew(episodeId, userId);
        }

        Long ttl = redisTemplate.getExpire(key);
        long ttlSeconds = ttl == null || ttl < 0 ? 0 : ttl;
        Long heldBy = parseHolder(currentHolder);
        throw new ResourceBusyException(
                "Episode " + episodeId + " is being edited by user " + currentHolder,
                heldBy,
                ttlSeconds);
    }

    @Override
    public EpisodeLockResponseDTO renew(Long episodeId, Long userId) {
        String key = lockKey(episodeId);
        String holder = String.valueOf(userId);

        Long result = redisTemplate.execute(
                RENEW_SCRIPT,
                List.of(key),
                holder,
                String.valueOf(LOCK_TTL.toMillis()));

        if (result == null || result < 0) {
            String currentHolder = redisTemplate.opsForValue().get(key);
            Long ttl = redisTemplate.getExpire(key);
            long ttlSeconds = ttl == null || ttl < 0 ? 0 : ttl;
            throw new ResourceBusyException(
                    "Cannot renew: episode " + episodeId + " is not locked by you",
                    parseHolder(currentHolder),
                    ttlSeconds);
        }
        log.debug("Episode {} lock renewed by user {}", episodeId, userId);
        return buildResponse(episodeId, userId, LOCK_TTL.toSeconds());
    }

    @Override
    public void release(Long episodeId, Long userId) {
        String key = lockKey(episodeId);
        Long result = redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(key),
                String.valueOf(userId));
        if (result != null && result > 0) {
            log.info("Episode {} lock released by user {}", episodeId, userId);
        }
    }

    @Override
    public void assertHeldBy(Long episodeId, Long userId) {
        String key = lockKey(episodeId);
        String currentHolder = redisTemplate.opsForValue().get(key);
        if (!String.valueOf(userId).equals(currentHolder)) {
            Long ttl = redisTemplate.getExpire(key);
            long ttlSeconds = ttl == null || ttl < 0 ? 0 : ttl;
            throw new ResourceBusyException(
                    "A valid episode edit lock is required before saving",
                    parseHolder(currentHolder),
                    ttlSeconds);
        }
    }

    private void requireEpisodeExists(Long episodeId) {
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResourceNotFoundException("Episode " + episodeId + " not found");
        }
    }

    private EpisodeLockResponseDTO buildResponse(Long episodeId, Long userId, long ttlSeconds) {
        return EpisodeLockResponseDTO.builder()
                .episodeId(episodeId)
                .heldBy(userId)
                .ttlSeconds(ttlSeconds)
                .expiresAt(Instant.now().plusSeconds(ttlSeconds))
                .build();
    }

    private static String lockKey(Long episodeId) {
        return LOCK_KEY_PREFIX + episodeId;
    }

    private static Long parseHolder(String holder) {
        if (holder == null)
            return null;
        try {
            return Long.valueOf(holder);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
