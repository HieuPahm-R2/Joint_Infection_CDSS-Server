package com.vietnam.pji.controller.auth;

import com.turkraft.springfilter.boot.Filter;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.services.auth.PermissionService;

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
@Tag(name = "Permissions", description = "Manage fine-grained authorization permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "Create permission", description = "Adds a new permission; fails if module+apiPath+method already exists")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Permission created"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PostMapping("/add-permission")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<Permission> create(@RequestBody Permission data) {
        if (this.permissionService.alreadyExistPermission(data)) {
            throw new IllegalArgumentException("Đã tồn tại, hãy thử lại!");
        }
        return new ResponseData<>(HttpStatus.CREATED.value(), "Permission created successfully",
                permissionService.create(data));
    }

    @Operation(summary = "Update permission", description = "Updates an existing permission by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PutMapping("/update-permission")
    public ResponseData<Void> update(@RequestBody Permission data) {
        permissionService.update(data);
        return new ResponseData<>(HttpStatus.OK.value(), "Permission updated successfully");
    }

    @Operation(summary = "Delete permission", description = "Deletes a permission by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Permission not found")
    })
    @DeleteMapping("/delete-permission/{id}")
    public ResponseData<Void> handleDelete(@PathVariable("id") long id) {
        permissionService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Permission deleted successfully");
    }

    @Operation(summary = "Get permission by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission detail"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "Permission not found")
    })
    @GetMapping("/permission/{id}")
    public ResponseData<Permission> getById(@PathVariable long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch permission successfully",
                permissionService.getById(id));
    }

    @Operation(summary = "List permissions", description = "Paginated permission list with springfilter support")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated permission list"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @GetMapping("/permissions")
    public ResponseData<PaginationResultDTO> handleFetchAllPermission(
            @Filter Specification<Permission> spec, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch permissions successfully",
                permissionService.fetchAll(spec, pageable));
    }
}
