package com.vietnam.pji.services.auth.impl;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.RoleDetailDTO;
import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.repository.PermissionRepository;
import com.vietnam.pji.repository.RoleRepository;
import com.vietnam.pji.services.auth.RoleService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RedisService redisService;
    private final RoleMapper roleMapper;

    @Override
    @Transactional
    public RoleDetailDTO create(Role data) {
        List<Long> pers = data.getPermissions().stream().map(item -> item.getId())
                .collect(Collectors.toList());
        if (pers.isEmpty()) {
            throw new IllegalArgumentException("Permission not be blank! Please check again!");
        }
        List<Permission> allPers = this.permissionRepository.findByIdIn(pers);
        data.setPermissions(allPers);
        Role saved = this.roleRepository.save(data);
        redisService.evictAllUserPermissions();
        return roleMapper.toDetail(saved);
    }

    @Override
    @Transactional
    public RoleDetailDTO update(Role data) {
        if (this.roleRepository.findById(data.getId()).isEmpty()) {
            throw new NoSuchElementException("Not found, check again?");
        } else {
            Optional<Role> rOptional = this.roleRepository.findById(data.getId());
            if (rOptional.isPresent()) {
                List<Long> allItems = data.getPermissions().stream().map(item -> item.getId())
                        .collect(Collectors.toList());
                List<Permission> allPermissions = this.permissionRepository.findByIdIn(allItems);
                data.setPermissions(allPermissions);
                data.setCreatedBy(rOptional.get().getCreatedBy());
                data.setCreatedAt(rOptional.get().getCreatedAt());
                Role saved = this.roleRepository.save(data);
                redisService.evictAllUserPermissions();
                return roleMapper.toDetail(saved);
            }
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDetailDTO fetchById(Long id) {
        Optional<Role> check = this.roleRepository.findById(id);
        if (check.isEmpty()) {
            throw new NoSuchElementException("Data not found!");
        }
        return roleMapper.toDetail(check.get());
    }

    @Override
    public void delete(Long id) {
        if (this.roleRepository.findById(id).isEmpty()) {
            throw new NoSuchElementException("Không tìm thấy dữ liệu! (Có thể do ID không hợp lệ)");
        }
        this.roleRepository.deleteById(id);
        redisService.evictAllUserPermissions();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResultDTO fetchAll(Specification<Role> spec, Pageable pageable) {
        Page<Role> page = this.roleRepository.findAll(spec, pageable);
        return PaginationResultDTO.of(page, roleMapper.toDetails(page.getContent()));
    }
}
