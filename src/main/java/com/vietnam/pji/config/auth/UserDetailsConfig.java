package com.vietnam.pji.config.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.vietnam.pji.services.auth.UserService;

import java.util.Collections;

@Component("userDetailsService")
public class UserDetailsConfig implements UserDetailsService {
    private final UserService userService;

    public UserDetailsConfig(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.vietnam.pji.model.auth.User user = this.userService.handleGetUserByUsername(username);
        if (user == null || user.getPassword() == null) {
            throw new UsernameNotFoundException("User not found with email: " + username);
        }
        return new User(user.getEmail(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE-USER")));
    }
}
