package com.vietnam.pji.config.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.repository.PermissionRepository;
import com.vietnam.pji.repository.RoleRepository;
import com.vietnam.pji.services.feat.RedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-syncs the {@code permissions} table with the actual set of HTTP
 * endpoints declared on this Spring application.
 *
 * This runner closes that loop: on every startup it scans
 * {@link RequestMappingHandlerMapping}, diffs against the DB, and inserts any
 * newly-declared {@code (method, path)} combinations.
 *
 * <p>
 * Newly added permissions are also auto-granted to the {@code ADMIN} role
 * so administrators don't lose visibility into features they shipped. Other
 * roles are left untouched — they remain a curated subset, exactly as a human
 * would have configured them.
 *
 * Finally the user-permission Redis cache is flushed so any in-flight
 * session picks the new grants up immediately instead of waiting out the
 * 10-minute TTL.
 *
 * Runs after {@link DatabaseInitializer} via {@code @Order} so the initial
 * ADMIN role exists by the time we try to add to it.
 */
@Component
// Run BEFORE DatabaseInitializer. Fresh DB path: PermissionSyncer inserts every
// declared endpoint with no ADMIN role yet (logs and skips the grant step
// Existing DB path: PermissionSyncer diffs + grants; DatabaseInitializer skips.
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class PermissionSyncer implements CommandLineRunner {

    private static final String ADMIN_ROLE = "ADMIN";

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RedisService redisService;
    // TransactionTemplate is used instead of @Transactional so transactional
    // boundaries are explicit and proxy-agnostic. CommandLineRunner methods
    // can fail to pick up @Transactional cleanly depending on bean-init order
    // and DevTools restart timing; TransactionTemplate avoids that footgun.
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(String... args) {
        log.info(">>>>>>>>> PERMISSION SYNC BEGINS");

        // 1. Inventory all (method, path) declared by current controllers.
        Set<EndpointKey> declared = collectDeclaredEndpoints();
        if (declared.isEmpty()) {
            log.warn("No request mappings found — skipping permission sync");
            return;
        }

        Integer inserted = transactionTemplate.execute(status -> doSync(declared));
        log.info(">>>>>>>>> PERMISSION SYNC END (inserted={})", inserted);
    }

    /**
     * All DB work happens inside a single transaction managed by
     * TransactionTemplate.
     */
    private int doSync(Set<EndpointKey> declared) {
        // 2. Build lookup of permissions already in DB, keyed by (method, path).
        List<Permission> existing = permissionRepository.findAll();
        Map<EndpointKey, Permission> existingByKey = new HashMap<>();
        for (Permission p : existing) {
            if (p.getApiPath() == null || p.getMethod() == null) {
                continue;
            }
            existingByKey.put(new EndpointKey(p.getMethod().toUpperCase(), p.getApiPath()), p);
        }

        // 3. Diff: which declared endpoints don't have a permission row yet.
        List<Permission> toInsert = new ArrayList<>();
        for (EndpointKey key : declared) {
            if (!existingByKey.containsKey(key)) {
                toInsert.add(buildPermission(key));
            }
        }

        // 4. Persist new permission rows (may be empty — that's fine).
        List<Permission> savedNew = toInsert.isEmpty()
                ? List.of()
                : permissionRepository.saveAll(toInsert);
        if (!savedNew.isEmpty()) {
            log.info("Permission sync: inserted {} new permission row(s)", savedNew.size());
        }

        // 5. Reconcile ADMIN grants. We always do this even when nothing was
        // inserted — recovers from any past run where insert succeeded but
        // the grant step crashed (e.g. LazyInitializationException), leaving
        // permissions in the table but unlinked from any role.
        int granted = reconcileAdminGrants(declared, existingByKey, savedNew);

        // 6. Existing sessions cache permission lists in Redis (10-min TTL).
        // Flush so new grants are visible on the very next request.
        if (!savedNew.isEmpty() || granted > 0) {
            try {
                redisService.evictAllUserPermissions();
            } catch (Exception e) {
                log.warn("Permission sync: failed to evict user-permissions cache: {}", e.getMessage());
            }
        }

        if (savedNew.isEmpty() && granted == 0) {
            log.info("Permission sync: nothing to do (declared={}, in DB={}, ADMIN already covers all)",
                    declared.size(), existing.size());
        }

        return savedNew.size();
    }

    /**
     * Ensure ADMIN holds a grant for every declared endpoint that exists in the
     * permissions table. Returns the number of grants added.
     */
    private int reconcileAdminGrants(Set<EndpointKey> declared,
            Map<EndpointKey, Permission> existingByKey,
            List<Permission> savedNew) {
        Optional<Role> adminOpt = roleRepository.findByNameWithPermissions(ADMIN_ROLE);
        if (adminOpt.isEmpty()) {
            // Fresh-DB scenario: DatabaseInitializer creates the ADMIN role later
            // in the same startup pass and grants everything via findAll().
            log.info("Permission sync: role {} not yet present (fresh DB) — "
                    + "DatabaseInitializer will grant on creation", ADMIN_ROLE);
            return 0;
        }
        Role admin = adminOpt.get();

        // Merge savedNew into the existingByKey lookup so we treat them as known.
        for (Permission p : savedNew) {
            existingByKey.put(new EndpointKey(p.getMethod().toUpperCase(), p.getApiPath()), p);
        }

        // Set of permission ids ADMIN already has.
        List<Permission> adminPerms = admin.getPermissions();
        if (adminPerms == null) {
            adminPerms = new ArrayList<>();
            admin.setPermissions(adminPerms);
        }
        Set<Long> alreadyGranted = new HashSet<>();
        for (Permission p : adminPerms) {
            alreadyGranted.add(p.getId());
        }

        // Anything declared but not yet granted to ADMIN gets added.
        List<Permission> toGrant = new ArrayList<>();
        for (EndpointKey key : declared) {
            Permission p = existingByKey.get(key);
            if (p != null && !alreadyGranted.contains(p.getId())) {
                toGrant.add(p);
            }
        }

        if (toGrant.isEmpty()) {
            return 0;
        }

        adminPerms.addAll(toGrant);
        roleRepository.save(admin);
        log.info("Permission sync: granted {} permission(s) to role {} ({} were orphaned, {} freshly inserted)",
                toGrant.size(), ADMIN_ROLE,
                toGrant.size() - savedNew.size(), savedNew.size());
        return toGrant.size();
    }

    private Set<EndpointKey> collectDeclaredEndpoints() {
        Set<EndpointKey> keys = new HashSet<>();
        Map<RequestMappingInfo, HandlerMethod> handlers = requestMappingHandlerMapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlers.entrySet()) {
            RequestMappingInfo info = entry.getKey();

            Set<String> patterns = extractPathPatterns(info);
            if (patterns.isEmpty())
                continue;

            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            // A mapping with no explicit method matches ANY — we skip those because
            // we can't enumerate "all methods" usefully, and the codebase always
            // declares an explicit verb via @GetMapping / @PostMapping / etc.
            if (methods.isEmpty())
                continue;

            for (String pattern : patterns) {
                if (pattern == null || pattern.isBlank())
                    continue;
                if (isSpringInternal(pattern))
                    continue;
                for (RequestMethod method : methods) {
                    keys.add(new EndpointKey(method.name(), pattern));
                }
            }
        }
        return keys;
    }

    private Set<String> extractPathPatterns(RequestMappingInfo info) {
        // Spring 6 prefers PathPatternsCondition over PatternsCondition.
        if (info.getPathPatternsCondition() != null) {
            Set<String> out = new HashSet<>();
            info.getPathPatternsCondition().getPatterns().forEach(p -> out.add(p.getPatternString()));
            return out;
        }
        if (info.getPatternsCondition() != null) {
            return new HashSet<>(info.getPatternsCondition().getPatterns());
        }
        return Set.of();
    }

    private boolean isSpringInternal(String pattern) {
        // Skip framework-provided handlers (error page, springdoc swagger, actuator).
        return pattern.startsWith("/error")
                || pattern.startsWith("/swagger")
                || pattern.startsWith("/v3/api-docs")
                || pattern.startsWith("/webjars");
    }

    private Permission buildPermission(EndpointKey key) {
        return new Permission(
                deriveName(key),
                key.path,
                key.method,
                deriveModule(key));
    }

    private String deriveName(EndpointKey key) {
        return "[auto] " + key.method + " " + key.path;
    }

    /**
     * Best-effort module label from the URL pattern. Falls back to {@code MISC}
     * when the URL doesn't look like {@code /api/v1/<segment>/...}. Admins can
     * rename modules in the UI after the fact — the sync only cares about
     * {@code (method, path)} for diffing.
     */
    private String deriveModule(EndpointKey key) {
        String path = key.path;
        // Strip optional /api/v1 prefix
        if (path.startsWith("/api/")) {
            int slash = path.indexOf('/', 5); // after "/api/"
            if (slash != -1)
                path = path.substring(slash);
        }
        // Take first segment
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        int next = trimmed.indexOf('/');
        String segment = next == -1 ? trimmed : trimmed.substring(0, next);
        if (segment.isBlank())
            return "MISC";
        // Path variables aren't useful as module names
        if (segment.startsWith("{"))
            return "MISC";
        return segment.toUpperCase().replace('-', '_');
    }

    /**
     * Composite key for diffing permissions; method is normalised to upper-case.
     */
    private static final class EndpointKey {
        final String method;
        final String path;

        EndpointKey(String method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof EndpointKey other))
                return false;
            return method.equals(other.method) && path.equals(other.path);
        }

        @Override
        public int hashCode() {
            return 31 * method.hashCode() + path.hashCode();
        }
    }
}
