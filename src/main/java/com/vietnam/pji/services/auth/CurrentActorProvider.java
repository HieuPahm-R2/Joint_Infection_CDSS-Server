package com.vietnam.pji.services.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietnam.pji.constant.UserStatus;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * The single place that answers "who is calling, and are they still allowed to
 * be calling?".
 *
 * <p>
 * Before this class, nine call sites resolved the caller independently: some
 * looked up only the JWT subject, some never checked account status, some never
 * checked whether the role was still active, and each paid a separate database
 * round trip. The result was that a deactivated account, or an account whose
 * role had been disabled, kept full clinical write access for the remaining life
 * of its access token - and, because the admin test was a bare role-name string
 * comparison, an <em>inactive</em> administrator kept the audited break-glass
 * bypass.
 *
 * <p>
 * Two invariants hold here, per
 * {@code docs/decisions/0008-general-authorization-policy.md}:
 *
 * <ol>
 * <li><strong>Deny by default.</strong> Every failure to establish identity,
 * status, role or role state throws. Nothing returns a partially populated
 * principal.</li>
 * <li><strong>Only an active ADMIN breaks glass.</strong>
 * {@link ActivePrincipal#admin()} is true only for an active account holding an
 * active ADMIN/SUPER_ADMIN role; an inactive administrator is resolved as an
 * ordinary user, so it cannot bypass record relationships.</li>
 * </ol>
 *
 * <p>
 * The lookup is memoized per bean instance, which is one request: callers that
 * need the actor several times within a request (authorization, then ownership,
 * then audit) pay for one query, and a mutation to the caller's own role inside
 * the request is reflected on the next call rather than being masked by a stale
 * value.
 */
@Service
@RequiredArgsConstructor
public class CurrentActorProvider {

    private final UserService userService;

    private ActivePrincipal resolved;

    /**
     * @return the validated caller of the current request
     * @throws ForbiddenException when there is no principal, no matching active
     *                            account, or no active role
     */
    @Transactional(readOnly = true)
    public ActivePrincipal require() {
        if (resolved != null) {
            return resolved;
        }

        String email = SecurityUtils.getCurrentUserLogin()
                .filter(login -> !login.isBlank())
                .orElseThrow(() -> new ForbiddenException("Authenticated user required"));

        User user = userService.handleGetUserByUsername(email);
        if (user == null || user.getId() == null) {
            throw new ForbiddenException("Authenticated user required");
        }
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new ForbiddenException("Your account is not active");
        }
        Role role = user.getRole();
        if (role == null || !role.isActive()) {
            throw new ForbiddenException("An active role is required");
        }

        resolved = ActivePrincipal.of(user, role.getName());
        return resolved;
    }

    /**
     * Resolves the caller without enforcing anything, for the narrow case of
     * recording who performed a system action (audit attribution, automatic
     * ownership enrolment) where the absence of an authenticated caller is a
     * legitimate outcome rather than a denial.
     *
     * <p>
     * Never use this as an authorization decision: an empty result must not be
     * read as "allowed". Authorization callers use {@link #require()}.
     *
     * @return the validated caller, or empty when the request is unauthenticated
     *         or the account is no longer active
     */
    @Transactional(readOnly = true)
    public java.util.Optional<ActivePrincipal> tryResolve() {
        try {
            return java.util.Optional.of(require());
        } catch (ForbiddenException notAnActivePrincipal) {
            return java.util.Optional.empty();
        }
    }
}
