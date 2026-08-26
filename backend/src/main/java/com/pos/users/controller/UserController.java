package com.pos.users.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.users.domain.PermissionCode;
import com.pos.users.dto.UserCreateRequest;
import com.pos.users.dto.UserResponse;
import com.pos.users.dto.UserStatusRequest;
import com.pos.users.dto.UserUpdateRequest;
import com.pos.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCode.USER_READ + "')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.of(userService.listUsers(), RequestCorrelation.currentId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCode.USER_READ + "')")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(userService.getUser(id), RequestCorrelation.currentId()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCode.USER_WRITE + "')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody @Valid UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(userService.createUser(request), RequestCorrelation.currentId()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCode.USER_WRITE + "')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable UUID id, @RequestBody @Valid UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(userService.updateUser(id, request), RequestCorrelation.currentId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCode.USER_ADMIN + "')")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(@PathVariable UUID id, @RequestBody @Valid UserStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.of(userService.updateStatus(id, request), RequestCorrelation.currentId()));
    }
}
