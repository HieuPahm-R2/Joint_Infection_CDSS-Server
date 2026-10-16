package com.vietnam.pji.services.auth;

import com.vietnam.pji.model.auth.User;

/**
 * The authenticated caller of the current request, resolved and validated
 * against the deny-by-default policy in
 *
 * <p>
 * An {@code ActivePrincipal} exists only for a user that is present, has
 * {@code ACTIVE} status, and holds an active role. A missing user, a
 * deactivated account or a disabled role produces a denial at resolution time,
 * not a half-populated principal, so a caller cannot accidentally treat
 * "unknown" as "allowed".
 *
 * @param userId   the persisted user id
 * @param email    the login (JWT subject) the principal was resolved from
 * @param roleName the active role's name
 * @param admin    whether the role is the break-glass administrator
 */
public record ActivePrincipal(Long userId, String email, String roleName, boolean admin) {

    /** @return the persisted user this principal was resolved from */
    public static ActivePrincipal of(User user, String roleName) {
        return new ActivePrincipal(user.getId(), user.getEmail(), roleName, ActivePrincipal.isAdminRole(roleName));
    }

    /** @return whether {@code roleName} is the audited break-glass administrator role */
    public static boolean isAdminRole(String roleName) {
        return "ADMIN".equalsIgnoreCase(roleName) || "SUPER_ADMIN".equalsIgnoreCase(roleName);
    }

    /** @return whether this principal holds the given role, case-insensitively */
    public boolean hasRole(String role) {
        return role != null && role.equalsIgnoreCase(roleName);
    }

    /** @return whether this principal matches another user's persisted id */
    public boolean is(Long otherUserId) {
        return otherUserId != null && otherUserId.equals(userId);
    }
}
