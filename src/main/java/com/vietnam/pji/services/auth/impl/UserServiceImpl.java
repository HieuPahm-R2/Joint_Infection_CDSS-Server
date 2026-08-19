package com.vietnam.pji.services.auth.impl;

import com.vietnam.pji.dto.request.ChangePasswordRequestDTO;
import com.vietnam.pji.dto.request.UpdateOwnProfileRequestDTO;
import com.vietnam.pji.dto.request.UserRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.UserDetailResponse;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.RoleRepository;
import com.vietnam.pji.repository.UserRepository;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final RedisService redisService;

    @Override
    public UserDetailResponse create(UserRequestDTO data) {
        if (userRepository.existsByEmail(data.getEmail())) {
            throw new InvalidDataException("User with this email already exists.");
        }
        if (data.getPassword() == null || data.getPassword().isBlank()) {
            throw new InvalidDataException("Password must not be blank");
        }
        User user = userMapper.toEntity(data);
        if (data.getRole().getId() != null) {
            Role role = roleRepository.findById(data.getRole().getId()).orElse(null);
            user.setRole(role);
        }
        user.setPassword(passwordEncoder.encode(data.getPassword()));
        return userMapper.toUserDetailResponse(userRepository.save(user));
    }

    @Override
    public UserDetailResponse update(UserRequestDTO data) {
        User user = userRepository.findById(data.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String incomingPassword = data.getPassword();
        userMapper.update(data, user);
        if (data.getRole().getId() != null) {
            Role role = roleRepository.findById(data.getRole().getId()).orElse(null);
            user.setRole(role);
        }
        if (incomingPassword != null && !incomingPassword.isBlank()) {
            user.setPassword(passwordEncoder.encode(incomingPassword));
        }
        UserDetailResponse response = userMapper.toUserDetailResponse(userRepository.save(user));
        redisService.evictUserPermissions(user.getEmail());
        return response;
    }

    @Override
    public UserDetailResponse getInfo(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toUserDetailResponse(user);
    }

    @Override
    public PaginationResultDTO getAll(Specification<User> spec, Pageable pageable) {
        Page<User> page = userRepository.findAll(spec, pageable);
        PaginationResultDTO.Meta mt = new PaginationResultDTO.Meta();
        mt.setPage(page.getNumber() + 1);
        mt.setPageSize(page.getSize());
        mt.setPages(page.getTotalPages());
        mt.setTotal(page.getTotalElements());

        List<UserDetailResponse> users = page.getContent().stream()
                .map(userMapper::toUserDetailResponse)
                .collect(Collectors.toList());

        PaginationResultDTO res = new PaginationResultDTO();
        res.setMeta(mt);
        res.setResult(users);
        return res;
    }

    @Override
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        redisService.evictUserPermissions(user.getEmail());
        userRepository.deleteById(id);
    }

    @Override
    public User handleGetUserByUsername(String username) {
        return userRepository.findByEmail(username);
    }

    @Override
    public User fetchWithTokenAndEmail(String token, String email) {
        return userRepository.findByRefreshTokenAndEmail(token, email);
    }

    @Override
    public void saveRefreshToken(String token, String email) {
        User user = handleGetUserByUsername(email);
        if (user != null) {
            user.setRefreshToken(token);
            userRepository.save(user);
        }
    }

    @Override
    public void updateLastLogin(String email) {
        User user = handleGetUserByUsername(email);
        if (user != null) {
            user.setLastLogin(Instant.now());
            userRepository.save(user);
        }
    }

    @Override
    @Transactional
    public User updateOwnProfile(String email, UpdateOwnProfileRequestDTO data) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }
        user.setFullName(data.getFullName());
        if (data.getPhone() != null) {
            user.setPhone(data.getPhone().isBlank() ? null : data.getPhone());
        }
        if (data.getDepartment() != null) {
            user.setDepartment(data.getDepartment().isBlank() ? null : data.getDepartment());
        }
        if (data.getAvatar() != null) {
            user.setAvatar(data.getAvatar().isBlank() ? null : data.getAvatar());
        }
        User saved = userRepository.save(user);
        initializeRolePermissions(saved);
        redisService.evictUserPermissions(saved.getEmail());
        return saved;
    }

    private void initializeRolePermissions(User user) {
        if (user.getRole() != null && user.getRole().getPermissions() != null) {
            user.getRole().getPermissions().size();
        }
    }

    @Override
    public void changeOwnPassword(String email, ChangePasswordRequestDTO data) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }
        if (!passwordEncoder.matches(data.getCurrentPassword(), user.getPassword())) {
            throw new InvalidDataException("Mật khẩu hiện tại không đúng.");
        }
        if (passwordEncoder.matches(data.getNewPassword(), user.getPassword())) {
            throw new InvalidDataException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPassword(passwordEncoder.encode(data.getNewPassword()));
        // Revoke mọi phiên đăng nhập sau khi đổi mật khẩu — cùng pattern với
        // PasswordRecoveryServiceImpl.resetPassword.
        user.setRefreshToken(null);
        userRepository.save(user);
        redisService.deleteRefreshToken(user.getEmail());
    }
}
