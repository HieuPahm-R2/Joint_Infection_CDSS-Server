package com.vietnam.pji.config.auth;

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

@ExtendWith(MockitoExtension.class)
class UserDetailsConfigTest {

    @Mock
    private UserService userService;

    private UserDetailsConfig userDetailsConfig;

    @BeforeEach
    void setUp() {
        userDetailsConfig = new UserDetailsConfig(userService);
    }

    @Test
    void loadUserByUsername_UserExists_ReturnsUserDetails() {
        User user = new User();
        user.setEmail("doctor@example.com");
        user.setPassword("encodedPassword");

        when(userService.handleGetUserByUsername("doctor@example.com")).thenReturn(user);

        UserDetails userDetails = userDetailsConfig.loadUserByUsername("doctor@example.com");

        assertNotNull(userDetails);
        assertEquals("doctor@example.com", userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE-USER")));
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
}
