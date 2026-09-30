package com.vietnam.pji.config.auth;

import com.vietnam.pji.constant.UserStatus;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.services.auth.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Pins the deny-by-default login contract: an account authenticates only when it has a
 * password, an ACTIVE status and an active role. Missing identity, status or role data is
 * a denial, never an implicit grant (see docs/decisions/0008-general-authorization-policy.md).
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsConfigTest {

    private static final String EMAIL = "doctor@example.com";

    @Mock
    private UserService userService;

    private UserDetailsConfig userDetailsConfig;

    @BeforeEach
    void setUp() {
        userDetailsConfig = new UserDetailsConfig(userService);
    }

    private User activeUserWithRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        role.setActive(true);

        User user = new User();
        user.setEmail(EMAIL);
        user.setPassword("encodedPassword");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        return user;
    }

    @Test
    void loadUserByUsername_ActiveUserWithActiveRole_ReturnsUserDetails() {
        when(userService.handleGetUserByUsername(EMAIL)).thenReturn(activeUserWithRole("DOCTOR"));

        UserDetails userDetails = userDetailsConfig.loadUserByUsername(EMAIL);

        assertNotNull(userDetails);
        assertEquals(EMAIL, userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR")));
    }

    @Test
    void loadUserByUsername_UserNotFound_ThrowsUsernameNotFoundException() {
        when(userService.handleGetUserByUsername("nonexistent@example.com")).thenReturn(null);

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsConfig.loadUserByUsername("nonexistent@example.com"));
    }

    @Test
    void loadUserByUsername_UserPasswordNull_ThrowsUsernameNotFoundException() {
        User user = new User();
        user.setEmail("nopassword@example.com");
        user.setPassword(null);

        when(userService.handleGetUserByUsername("nopassword@example.com")).thenReturn(user);

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsConfig.loadUserByUsername("nopassword@example.com"));
    }

    @Test
    void loadUserByUsername_StatusNull_ThrowsUsernameNotFoundException() {
        // Regression guard: users.status is nullable with no database default, so accounts
        // created before migration V35 had a NULL status and could never log in.
        User user = activeUserWithRole("DOCTOR");
        user.setStatus(null);

        when(userService.handleGetUserByUsername(EMAIL)).thenReturn(user);

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsConfig.loadUserByUsername(EMAIL));
    }

    @Test
    void loadUserByUsername_StatusInactive_ThrowsUsernameNotFoundException() {
        User user = activeUserWithRole("DOCTOR");
        user.setStatus(UserStatus.INACTIVE);

        when(userService.handleGetUserByUsername(EMAIL)).thenReturn(user);

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsConfig.loadUserByUsername(EMAIL));
    }

    @Test
    void loadUserByUsername_RoleNull_ThrowsUsernameNotFoundException() {
        User user = activeUserWithRole("DOCTOR");
        user.setRole(null);

        when(userService.handleGetUserByUsername(EMAIL)).thenReturn(user);

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsConfig.loadUserByUsername(EMAIL));
    }

    @Test
    void loadUserByUsername_RoleInactive_ThrowsUsernameNotFoundException() {
        User user = activeUserWithRole("DOCTOR");
        user.getRole().setActive(false);

        when(userService.handleGetUserByUsername(EMAIL)).thenReturn(user);

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsConfig.loadUserByUsername(EMAIL));
    }
}
