package com.vietnam.pji.controller.auth;

import com.turkraft.springfilter.boot.Filter;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.dto.response.RoleDetailDTO;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.repository.RoleRepository;
import com.vietnam.pji.services.auth.RoleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Manage user roles and their attached permissions")
public class RoleController {

    private final RoleService roleService;
    private final RoleRepository roleRepository;

    @Operation(summary = "Create role", description = "Adds a new role; fails if the role name already exists")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<RoleDetailDTO> create(@RequestBody Role data) {
        if (roleRepository.existsByName(data.getName())) {
            throw new IllegalArgumentException("Dữ liệu bị trùng lặp");
        }
        return new ResponseData<>(HttpStatus.CREATED.value(), "Role created successfully", roleService.create(data));
    }

    @Operation(summary = "Update role", description = "Updates an existing role and its permission set")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PutMapping("/roles")
    public ResponseData<Void> update(@RequestBody Role data) {
        roleService.update(data);
        return new ResponseData<>(HttpStatus.OK.value(), "Role updated successfully");
    }

    @Operation(summary = "Delete role", description = "Deletes a role by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Role not found")
    })
    @DeleteMapping("/roles/{id}")
    public ResponseData<Void> delete(@PathVariable("id") long id) {
        roleService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Role deleted successfully");
    }

    @Operation(summary = "Get role by id", description = "Returns a role with its permission detail")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role detail with permissions"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Role not found")
    })
    @GetMapping("/role/{id}")
    public ResponseData<RoleDetailDTO> handleFetchSingle(@PathVariable("id") long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch role successfully", roleService.fetchById(id));
    }

    @Operation(summary = "List roles", description = "Paginated role list with springfilter support")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated role list"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @GetMapping("/roles")
    public ResponseData<PaginationResultDTO> handleFetchAllRole(
            @Filter Specification<Role> spec, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch roles successfully",
                roleService.fetchAll(spec, pageable));
    }
}
