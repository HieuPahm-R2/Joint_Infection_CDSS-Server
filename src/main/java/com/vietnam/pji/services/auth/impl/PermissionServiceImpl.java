package com.vietnam.pji.services.auth.impl;

import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.repository.PermissionRepository;
import com.vietnam.pji.services.auth.PermissionService;

import lombok.RequiredArgsConstructor;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final PermissionRepository permissionRepository;

    @Override
    public Permission create(Permission data) {
        return this.permissionRepository.save(data);
    }

    @Override
    public Permission update(Permission data) {
        if (data.getId() == null || this.permissionRepository.findById(data.getId()).isEmpty()) {
            if (this.isEqualName(data.getName())) {
                throw new IllegalArgumentException("Dữ liệu bị trùng lặp!!");
            }
        } else {
            if (this.alreadyExistPermission(data)) {
                throw new IllegalArgumentException("Dữ liệu đã tồn tại! (có thể do trùng apiPath or module or method)");
            } else {
                Optional<Permission> opt = this.permissionRepository.findById(data.getId());
                if (opt.isPresent()) {
                    data.setCreatedBy(opt.get().getCreatedBy());
                    data.setCreatedAt(opt.get().getCreatedAt());
                    return this.permissionRepository.save(data);
                }
            }
        }
        return null;
    }

    @Override
    public Permission getById(Long id) {
        Optional<Permission> check = this.permissionRepository.findById(id);
        if (check.isEmpty()) {
            throw new ResourceNotFoundException("Not Found");
        }
        return check.get();
    }

    @Override
    public void delete(Long id) {
        Optional<Permission> check = this.permissionRepository.findById(id);
        if (check.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy dữ liệu!");
        }
        Permission res = check.get();
        res.getRoles().stream().forEach(item -> item.getPermissions().remove(res));
        this.permissionRepository.delete(res);
    }

    @Override
    public PaginationResultDTO fetchAll(Specification<Permission> spec, Pageable pageable) {
        Page<Permission> page = this.permissionRepository.findAll(spec, pageable);
        return PaginationResultDTO.of(page);
    }

    @Override
    public boolean alreadyExistPermission(Permission data) {
        return this.permissionRepository.existsByModuleAndApiPathAndMethod(data.getModule(), data.getApiPath(),
                data.getMethod());
    }

    @Override
    public boolean isEqualName(String s) {
        return this.permissionRepository.existsByName(s);
    }
}
