package com.vietnam.pji.controller.auth;

import com.turkraft.springfilter.boot.Filter;
import com.vietnam.pji.dto.request.UserRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.dto.response.UserDetailResponse;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.services.auth.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}")
@Validated
@Slf4j
@Tag(name = "Users", description = "Manage user accounts (create, update, delete, list)")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create user", description = "Creates a new user account with assigned role")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PostMapping("/add-user")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseData<UserDetailResponse> createUser(@Valid @RequestBody UserRequestDTO request) {
        return new ResponseData<>(HttpStatus.CREATED.value(), "User created successfully", userService.create(request));
    }

    @Operation(summary = "Update user", description = "Updates an existing user profile and role")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PutMapping("/update-user")
    public ResponseData<Void> updateUser(@Valid @RequestBody UserRequestDTO request) {
        userService.update(request);
        return new ResponseData<>(HttpStatus.OK.value(), "User updated successfully");
    }

    @Operation(summary = "Get user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User detail"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/user/{id}")
    public ResponseData<UserDetailResponse> getInfo(@PathVariable long id) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch user successfully", userService.getInfo(id));
    }

    @Operation(summary = "Delete user", description = "Removes a user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/delete-user/{id}")
    public ResponseData<Void> deleteUser(@PathVariable long id) {
        userService.delete(id);
        return new ResponseData<>(HttpStatus.OK.value(), "User deleted successfully");
    }

    @Operation(summary = "List users", description = "Paginated user list with springfilter support")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated user list"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @GetMapping("/users")
    public ResponseData<PaginationResultDTO> getAllUsersInfo(
            @Filter Specification<User> spec, Pageable pageable) {
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch users successfully",
                userService.getAll(spec, pageable));
    }
}
