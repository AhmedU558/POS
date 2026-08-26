package com.pos.users.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.users.domain.Permission;
import com.pos.users.domain.Role;
import com.pos.users.dto.PermissionResponse;
import com.pos.users.dto.RoleCreateRequest;
import com.pos.users.dto.RolePermissionsRequest;
import com.pos.users.dto.RoleResponse;
import com.pos.users.repository.PermissionRepository;
import com.pos.users.repository.RoleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditRecorder auditRecorder;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository, AuditRecorder auditRecorder) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    public RoleResponse createRole(RoleCreateRequest request) {
        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Role name is already in use");
        }

        Set<Permission> permissions = permissionRepository.findAllById(request.permissionIds())
                .stream().collect(Collectors.toSet());
        if (permissions.size() != request.permissionIds().size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "One or more permissions do not exist");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        requirePermissionAdministrationAuthority(permissions, authentication);

        Role role = new Role(request.name(), request.description());
        permissions.forEach(role::grant);

        Role savedRole = roleRepository.save(role);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "ROLE_CREATED",
                "Role",
                savedRole.getId()
        ));

        return RoleResponse.from(savedRole);
    }

    public RoleResponse updateRolePermissions(UUID id, RolePermissionsRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found"));

        Set<Permission> permissions = permissionRepository.findAllById(request.permissionIds())
                .stream().collect(Collectors.toSet());
        if (permissions.size() != request.permissionIds().size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "One or more permissions do not exist");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        requirePermissionAdministrationAuthority(permissions, authentication);

        Set<Permission> currentPermissions = Set.copyOf(role.getPermissions());
        currentPermissions.forEach(role::revoke);
        permissions.forEach(role::grant);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "ROLE_PERMISSIONS_UPDATED",
                "Role",
                role.getId()
        ));

        return RoleResponse.from(role);
    }

    private void requirePermissionAdministrationAuthority(Set<Permission> permissions, Authentication authentication) {
        Set<String> callerAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        for (Permission p : permissions) {
            if (!callerAuthorities.contains(p.getCode())) {
                throw new ApiException(
                        ErrorCode.ACCESS_DENIED,
                        "Cannot grant permission you do not possess: " + p.getCode()
                );
            }
        }
    }
}
