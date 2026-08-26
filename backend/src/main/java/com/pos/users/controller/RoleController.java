package com.pos.users.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.users.domain.PermissionCode;
import com.pos.users.dto.PermissionResponse;
import com.pos.users.dto.RoleCreateRequest;
import com.pos.users.dto.RolePermissionsRequest;
import com.pos.users.dto.RoleResponse;
import com.pos.users.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('" + PermissionCode.ROLE_READ + "')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.of(roleService.listRoles(), RequestCorrelation.currentId()));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('" + PermissionCode.ROLE_WRITE + "')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@RequestBody @Valid RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(roleService.createRole(request), RequestCorrelation.currentId()));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCode.ROLE_READ + "')")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> listPermissions() {
        return ResponseEntity.ok(ApiResponse.of(roleService.listPermissions(), RequestCorrelation.currentId()));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCode.ROLE_WRITE + "')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRolePermissions(@PathVariable UUID id, @RequestBody @Valid RolePermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.of(roleService.updateRolePermissions(id, request), RequestCorrelation.currentId()));
    }
}
