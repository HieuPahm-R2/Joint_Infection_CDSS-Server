package com.vietnam.pji.services.auth.impl;

import com.vietnam.pji.dto.request.UpdateOwnProfileRequestDTO;
import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.RoleRepository;
import com.vietnam.pji.repository.UserRepository;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RedisService redisService;

    @InjectMocks
    private UserServiceImpl service;

    @Test
    void updateOwnProfileInitializesRolePermissionsBeforeTransactionCloses() {
        User user = new User();
        user.setEmail("doctor@example.com");
        Role role = mock(Role.class);
        @SuppressWarnings("unchecked")
        List<Permission> permissions = mock(List.class);
        when(role.getPermissions()).thenReturn(permissions);
        user.setRole(role);

        UpdateOwnProfileRequestDTO request = new UpdateOwnProfileRequestDTO();
        request.setFullName("Bác sĩ Nguyễn");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        User updated = service.updateOwnProfile(user.getEmail(), request);

        assertEquals("Bác sĩ Nguyễn", updated.getFullName());
        verify(permissions).size();
        verify(redisService).evictUserPermissions(user.getEmail());
    }
}
