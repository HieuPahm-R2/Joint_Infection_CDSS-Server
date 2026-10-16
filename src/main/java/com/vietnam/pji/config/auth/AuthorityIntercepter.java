package com.vietnam.pji.config.auth;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.UserStatus;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Functional-layer authorization: does the caller's active role grant the
 * {@code METHOD:path} being invoked?
 *
 * <p>
 * This is layer 1 of {@code docs/decisions/0008-general-authorization-policy.md};
 * it says nothing about which clinical records the caller may reach, which is
 * layer 2 in the service layer.
 *
 * <p>
 * Two structural problems were fixed here without changing the decision:
 *
 * <ul>
 * <li>The handler used to be {@code @Transactional}, so every non-whitelisted
 * request opened a database transaction and held a Hikari connection while
 * performing a Redis round trip. Permission lookup is a read against a
 * Redis-cached value; the annotation only created pool pressure.</li>
 * <li>{@code System.out.println} tracing ran on every request, and a dead
 * {@code if (permissionKeys != null)} re-check plus a comment claiming that a
 * missing role "passes through" contradicted the code, which denies.</li>
 * </ul>
 */
@Slf4j
@Component
public class AuthorityIntercepter implements HandlerInterceptor {

    /**
     * Cached absence of permissions. Denies, and is cached so a caller without an
     * active role does not pay a database lookup per request. The short TTL bounds
     * how long a revoked role can still be presented before the next lookup.
     */
    private static final String NO_ROLE_SENTINEL = "NO_ROLE";

    private static final long PERMISSIONS_CACHE_TTL = 600; // 10 minutes

    @Autowired
    UserService userService;

    @Autowired
    RedisService redisService;

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String httpMethod = request.getMethod();

        String email = SecurityUtils.getCurrentUserLogin().orElse("");
        if (email.isBlank() || path == null) {
            throw new ForbiddenException("Authenticated principal and matched route are required");
        }

        List<String> permissionKeys = loadPermissions(email);
        if (permissionKeys == null) {
            // Deny by default: an account with no active role may invoke nothing.
            throw new ForbiddenException("An active role is required");
        }

        String requestKey = httpMethod + ":" + path;
        if (!permissionKeys.contains(requestKey)) {
            throw new ForbiddenException("You don't have permission to access");
        }
        return true;
    }

    /**
     * Load permissions from the Redis cache, falling back to the database.
     *
     * @return the caller's {@code METHOD:path} keys, or {@code null} when the
     *         account has no usable role - which callers must treat as a denial
     */
    private List<String> loadPermissions(String email) {
        try {
            String cached = redisService.getCachedUserPermissions(email);
            if (cached != null) {
                if (NO_ROLE_SENTINEL.equals(cached)) {
                    return null;
                }
                return objectMapper.readValue(cached, new TypeReference<List<String>>() {
                });
            }
        } catch (Exception e) {
            log.warn("Failed to read permission cache for {}, falling back to DB", email);
        }

        User user = this.userService.handleGetUserByUsername(email);
        if (user != null && UserStatus.ACTIVE.equals(user.getStatus())) {
            Role role = user.getRole();
            if (role != null && role.isActive()) {
                List<Permission> permissions = role.getPermissions();
                List<String> keys = permissions.stream()
                        .map(permission -> permission.getMethod() + ":" + permission.getApiPath())
                        .collect(Collectors.toList());
                cachePermissionsSafe(email, keys);
                return keys;
            }
        }

        cachePermissionsSafe(email, null);
        return null;
    }

    private void cachePermissionsSafe(String email, List<String> keys) {
        try {
            String value = (keys != null) ? objectMapper.writeValueAsString(keys) : NO_ROLE_SENTINEL;
            redisService.cacheUserPermissions(email, value, PERMISSIONS_CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache permissions for {}", email);
        }
    }
}
