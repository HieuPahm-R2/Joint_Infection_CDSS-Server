package com.vietnam.pji.services.feat.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.vietnam.pji.services.feat.RedisService;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisServiceImpl implements RedisService {
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";
    private static final String ACCESS_TOKEN_BLACKLIST_KEY_PREFIX = "access_token_blacklist:";
    private static final String USER_PERMISSIONS_KEY_PREFIX = "user_permissions:";
    private static final String SNAPSHOT_KEY_PREFIX = "snapshot:";
    private static final String RUN_DETAIL_KEY_PREFIX = "run_detail:v2:";
    private static final String ACTIVE_SESSION_KEY_PREFIX = "auth:active_session:";
    private static final String CANCEL_RUN_KEY_PREFIX = "cancel:run:";

    @Override
    public void saveRefreshToken(String email, String refreshToken, long expirationTimeInSeconds) {
        String key = REFRESH_TOKEN_KEY_PREFIX + email;
        redisTemplate.opsForValue().set(key, refreshToken, expirationTimeInSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getRefreshToken(String email) {
        String key = REFRESH_TOKEN_KEY_PREFIX + email;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void deleteRefreshToken(String email) {
        String key = REFRESH_TOKEN_KEY_PREFIX + email;
        redisTemplate.delete(key);
    }

    @Override
    public boolean validateRefreshToken(String email, String refreshToken) {
        String storedToken = getRefreshToken(email);
        return Objects.equals(storedToken, refreshToken);
    }

    @Override
    public void saveActiveSession(String email, String sessionId, long expirationTimeInSeconds) {
        redisTemplate.opsForValue().set(ACTIVE_SESSION_KEY_PREFIX + email, sessionId,
                expirationTimeInSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getActiveSession(String email) {
        return redisTemplate.opsForValue().get(ACTIVE_SESSION_KEY_PREFIX + email);
    }

    @Override
    public void deleteActiveSession(String email) {
        redisTemplate.delete(ACTIVE_SESSION_KEY_PREFIX + email);
    }

    @Override
    public void blacklistAccessToken(String accessToken, long expirationTimeInSeconds) {
        String key = ACCESS_TOKEN_BLACKLIST_KEY_PREFIX + accessToken;
        redisTemplate.opsForValue().set(key, "blacklisted", expirationTimeInSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        String key = ACCESS_TOKEN_BLACKLIST_KEY_PREFIX + accessToken;
        return redisTemplate.hasKey(key);
    }

    // ===== User Permission Caching =====

    @Override
    public void cacheUserPermissions(String email, String permissionsJson, long ttlSeconds) {
        String key = USER_PERMISSIONS_KEY_PREFIX + email;
        redisTemplate.opsForValue().set(key, permissionsJson, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getCachedUserPermissions(String email) {
        String key = USER_PERMISSIONS_KEY_PREFIX + email;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void evictUserPermissions(String email) {
        String key = USER_PERMISSIONS_KEY_PREFIX + email;
        redisTemplate.delete(key);
    }

    @Override
    public void evictAllUserPermissions() {
        Set<String> keys = redisTemplate.keys(USER_PERMISSIONS_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Evicted {} user permission cache entries", keys.size());
        }
    }

    // ===== Episode Snapshot Caching =====

    @Override
    public void cacheSnapshot(Long episodeId, String snapshotJson, long ttlSeconds) {
        String key = SNAPSHOT_KEY_PREFIX + episodeId;
        redisTemplate.opsForValue().set(key, snapshotJson, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getCachedSnapshot(Long episodeId) {
        String key = SNAPSHOT_KEY_PREFIX + episodeId;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void evictSnapshotCache(Long episodeId) {
        String key = SNAPSHOT_KEY_PREFIX + episodeId;
        redisTemplate.delete(key);
    }

    // ===== AI Run Detail Caching =====

    @Override
    public void cacheRunDetail(Long runId, String detailJson, long ttlSeconds) {
        String key = RUN_DETAIL_KEY_PREFIX + runId;
        redisTemplate.opsForValue().set(key, detailJson, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getCachedRunDetail(Long runId) {
        String key = RUN_DETAIL_KEY_PREFIX + runId;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void evictRunDetail(Long runId) {
        String key = RUN_DETAIL_KEY_PREFIX + runId;
        redisTemplate.delete(key);
    }

    // ===== AI Run Cancellation Signaling =====

    @Override
    public void markRunCancelled(Long runId, long ttlSeconds) {
        if (runId == null)
            return;
        String key = CANCEL_RUN_KEY_PREFIX + runId;
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean isRunCancelled(Long runId) {
        if (runId == null)
            return false;
        String key = CANCEL_RUN_KEY_PREFIX + runId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void clearRunCancelled(Long runId) {
        if (runId == null)
            return;
        String key = CANCEL_RUN_KEY_PREFIX + runId;
        redisTemplate.delete(key);
    }
}
