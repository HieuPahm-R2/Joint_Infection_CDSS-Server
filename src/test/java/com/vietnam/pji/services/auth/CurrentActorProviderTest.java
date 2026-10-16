package com.vietnam.pji.services.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.vietnam.pji.constant.UserStatus;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.utils.SecurityUtils;

/**
 * Pins the active-principal rule from
 * {@code docs/decisions/0008-general-authorization-policy.md}: identity, account
 * status, role presence and role state are all required, and only an <em>active</em>
 * administrator gets the break-glass flag.
 */
class CurrentActorProviderTest {

    private final UserService userService = mock(UserService.class);
    private final CurrentActorProvider provider = new CurrentActorProvider(userService);

    @Test
    void activeUserWithActiveRoleResolves() {
        when(userService.handleGetUserByUsername("doctor@example.test")).thenReturn(user(UserStatus.ACTIVE, "DOCTOR", true));

        as("doctor@example.test", () -> {
            ActivePrincipal principal = provider.require();
            assertThat(principal.userId()).isEqualTo(11L);
            assertThat(principal.roleName()).isEqualTo("DOCTOR");
            assertThat(principal.admin()).isFalse();
        });
    }

    @Test
    void administratorIsFlaggedForBreakGlass() {
        when(userService.handleGetUserByUsername("admin@example.test")).thenReturn(user(UserStatus.ACTIVE, "ADMIN", true));

        as("admin@example.test", () -> assertThat(provider.require().admin()).isTrue());
    }

    @Test
    void inactiveAdministratorLosesBreakGlass() {
        // The previous implementation tested admin by role-name string only, so a
        // deactivated administrator kept the record-relationship bypass.
        when(userService.handleGetUserByUsername("admin@example.test"))
                .thenReturn(user(UserStatus.INACTIVE, "ADMIN", true));

        as("admin@example.test", () -> assertThatThrownBy(() -> provider.require())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not active"));
    }

    @Test
    void disabledRoleIsRejected() {
        when(userService.handleGetUserByUsername("doctor@example.test"))
                .thenReturn(user(UserStatus.ACTIVE, "DOCTOR", false));

        as("doctor@example.test", () -> assertThatThrownBy(() -> provider.require())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("active role"));
    }

    @Test
    void userWithoutRoleIsRejected() {
        User user = user(UserStatus.ACTIVE, "DOCTOR", true);
        user.setRole(null);
        when(userService.handleGetUserByUsername("doctor@example.test")).thenReturn(user);

        as("doctor@example.test", () -> assertThatThrownBy(() -> provider.require())
                .isInstanceOf(ForbiddenException.class));
    }

    @Test
    void unknownAccountIsRejected() {
        when(userService.handleGetUserByUsername("ghost@example.test")).thenReturn(null);

        as("ghost@example.test", () -> assertThatThrownBy(() -> provider.require())
                .isInstanceOf(ForbiddenException.class));
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.empty());

            assertThatThrownBy(() -> provider.require()).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void tryResolveReportsAbsenceWithoutThrowing() {
        when(userService.handleGetUserByUsername("admin@example.test"))
                .thenReturn(user(UserStatus.INACTIVE, "ADMIN", true));

        as("admin@example.test", () -> assertThat(provider.tryResolve()).isEmpty());
    }

    private void as(String email, Runnable assertion) {
        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of(email));
            assertion.run();
        }
    }

    private User user(UserStatus status, String roleName, boolean roleActive) {
        User user = User.builder().email("user@example.test").build();
        user.setId(11L);
        user.setStatus(status);
        user.setRole(new Role(roleName, "", roleActive, null, null));
        return user;
    }
}
