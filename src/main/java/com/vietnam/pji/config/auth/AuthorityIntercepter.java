package com.vietnam.pji.config.auth;

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
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class AuthorityIntercepter implements HandlerInterceptor {
    @Autowired
    UserService userService;

    @Autowired
    RedisService redisService;

    @Autowired
    ObjectMapper objectMapper;

    private static final long PERMISSIONS_CACHE_TTL = 600; // 10 minutes
    private static final String NO_ROLE_SENTINEL = "NO_ROLE";

    @Transactional
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String requestURI = request.getRequestURI();
        String httpMethod = request.getMethod();
        System.out.println(">>> RUN preHandle");
        System.out.println(">>> path= " + path);
        System.out.println(">>> httpMethod= " + httpMethod);
        System.out.println(">>> requestURI= " + requestURI);
        // Check Permission
        String email = SecurityUtils.getCurrentUserLogin().isPresent()
                ? SecurityUtils.getCurrentUserLogin().get()
                : "";
        if (email.isEmpty() || path == null) {
            throw new ForbiddenException("Authenticated principal and matched route are required");
        }
        if (!email.isEmpty()) {
            List<String> permissionKeys = loadPermissions(email);
            if (permissionKeys == null) {
                throw new ForbiddenException("An active role is required");
            }
            if (permissionKeys != null) {
                String requestKey = httpMethod + ":" + path;
                if (!permissionKeys.contains(requestKey)) {
                    throw new ForbiddenException("You don't have permission to access");
                }
            }
            // permissionKeys == null means user has no role → pass through (matches
            // original behavior)
        }
        return true;
    }

    /**
     * Load permissions from Redis cache, fallback to DB on miss.
     * Returns null if user has no role (pass through), or a list of "METHOD:path"
     * strings.
     */
    private List<String> loadPermissions(String email) {
        // Try cache first
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

        // Cache miss — load from DB
        User user = this.userService.handleGetUserByUsername(email);
        if (user != null && UserStatus.ACTIVE.equals(user.getStatus())) {
            Role role = user.getRole();
            if (role != null && role.isActive()) {
                List<Permission> permissions = role.getPermissions();
                List<String> keys = permissions.stream()
                        .map(p -> p.getMethod() + ":" + p.getApiPath())
                        .collect(Collectors.toList());
                cachePermissionsSafe(email, keys);
                return keys;
            }
        }

        // No user or no role — cache sentinel to avoid repeated DB lookups
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
