package com.vietnam.pji.config.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.PermissionRepository;
import com.vietnam.pji.repository.RoleRepository;
import com.vietnam.pji.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DatabaseInitializerTest {

    private static final String ADMIN_EMAIL = "admin@example.test";
    private static final String ADMIN_PASSWORD = "bootstrap-secret";

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DatabaseInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DatabaseInitializer(roleRepository, permissionRepository, userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "bootstrapAdminEnabled", true);
        ReflectionTestUtils.setField(initializer, "bootstrapAdminEmail", ADMIN_EMAIL);
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", ADMIN_PASSWORD);
        ReflectionTestUtils.setField(initializer, "bootstrapAdminFullName", "System Admin");

        when(permissionRepository.count()).thenReturn(1L);
    }

    @Test
    void createsAdminRoleAndBootstrapUserWhenAnotherRoleAlreadyExists() throws Exception {
        Permission permission = new Permission("Get patients", "/api/v1/patients", "GET", "PATIENTS");
        // V28 has already created PHARMACIST, so the old count-based initializer skipped ADMIN.
        lenient().when(roleRepository.count()).thenReturn(1L);
        when(roleRepository.findByName("ADMIN")).thenReturn(null);
        when(permissionRepository.findAll()).thenReturn(List.of(permission));
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(null);
        when(passwordEncoder.encode(ADMIN_PASSWORD)).thenReturn("encoded-password");

        initializer.run();

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(roleRepository).save(roleCaptor.capture());
        verify(userRepository).save(userCaptor.capture());

        Role savedRole = roleCaptor.getValue();
        User savedUser = userCaptor.getValue();
        assertThat(savedRole.getName()).isEqualTo("ADMIN");
        assertThat(savedRole.getPermissions()).containsExactly(permission);
        assertThat(savedUser.getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(savedUser.getRole()).isSameAs(savedRole);
    }

    @Test
    void createsBootstrapAdminByEmailWhenOtherUsersAlreadyExist() throws Exception {
        Role adminRole = role("ADMIN");
        // An unrelated user must not suppress creation of the configured bootstrap account.
        lenient().when(userRepository.count()).thenReturn(1L);
        when(roleRepository.findByName("ADMIN")).thenReturn(adminRole);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(null);
        when(passwordEncoder.encode(ADMIN_PASSWORD)).thenReturn("encoded-password");

        initializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isSameAs(adminRole);
    }

    @Test
    void repairsMissingRoleOnExistingBootstrapAdmin() throws Exception {
        Role adminRole = role("ADMIN");
        User bootstrapAdmin = new User();
        bootstrapAdmin.setEmail(ADMIN_EMAIL);
        // Reproduce the persisted state created by the old initializer.
        lenient().when(userRepository.count()).thenReturn(1L);
        when(roleRepository.findByName("ADMIN")).thenReturn(adminRole);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(bootstrapAdmin);

        initializer.run();

        assertThat(bootstrapAdmin.getRole()).isSameAs(adminRole);
        verify(userRepository).save(bootstrapAdmin);
        verify(passwordEncoder, never()).encode(ADMIN_PASSWORD);
    }

    @Test
    void doesNotOverwriteAnExistingBootstrapUsersRole() throws Exception {
        Role adminRole = role("ADMIN");
        Role pharmacistRole = role("PHARMACIST");
        User bootstrapUser = new User();
        bootstrapUser.setEmail(ADMIN_EMAIL);
        bootstrapUser.setRole(pharmacistRole);
        when(roleRepository.findByName("ADMIN")).thenReturn(adminRole);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(bootstrapUser);

        initializer.run();

        assertThat(bootstrapUser.getRole()).isSameAs(pharmacistRole);
        verify(userRepository, never()).save(bootstrapUser);
    }

    @Test
    void rejectsBlankPasswordWhenBootstrapAdminMustBeCreated() {
        Role adminRole = role("ADMIN");
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", " ");
        when(roleRepository.findByName("ADMIN")).thenReturn(adminRole);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(null);

        assertThatThrownBy(() -> initializer.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.bootstrap-admin.password");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        role.setActive(true);
        return role;
    }
}
