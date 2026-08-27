package com.pos.users.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.users.domain.Role;
import com.pos.organization.domain.Store;
import com.pos.users.domain.User;
import com.pos.users.dto.UserCreateRequest;
import com.pos.users.dto.UserResponse;
import com.pos.users.dto.UserStatusRequest;
import com.pos.users.dto.UserUpdateRequest;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import com.pos.organization.repository.StoreRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, StoreRepository storeRepository, PasswordEncoder passwordEncoder, AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Username is already in use");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Email is already in use");
        }

        Set<Role> roles = roleRepository.findAllById(request.roleIds())
                .stream().collect(Collectors.toSet());
        if (roles.size() != request.roleIds().size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "One or more roles do not exist");
        }
        
        Set<Store> stores = storeRepository.findAllById(request.storeIds())
                .stream().collect(Collectors.toSet());
        if (stores.size() != request.storeIds().size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "One or more stores do not exist");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        requireRoleAdministrationAuthority(roles, authentication);
        requireStoreAdministrationAuthority(stores, userDetails.getId());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.username(), encodedPassword, request.firstName(), request.lastName());
        user.setEmail(request.email());
        user.requirePasswordChange();

        roles.forEach(user::assignRole);
        stores.forEach(user::assignStore);

        User savedUser = userRepository.save(user);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "USER_CREATED",
                "User",
                savedUser.getId()
        ));

        return UserResponse.from(savedUser);
    }

    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        if (!user.getEmail().equals(request.email()) && userRepository.findByEmail(request.email()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Email is already in use");
        }

        Set<Role> roles = roleRepository.findAllById(request.roleIds())
                .stream().collect(Collectors.toSet());
        if (roles.size() != request.roleIds().size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "One or more roles do not exist");
        }
        
        Set<Store> stores = storeRepository.findAllById(request.storeIds())
                .stream().collect(Collectors.toSet());
        if (stores.size() != request.storeIds().size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "One or more stores do not exist");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        requireRoleAdministrationAuthority(roles, authentication);
        requireStoreAdministrationAuthority(stores, userDetails.getId());

        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        Set<Role> currentRoles = Set.copyOf(user.getRoles());
        currentRoles.forEach(user::removeRole);
        roles.forEach(user::assignRole);
        
        Set<Store> currentStores = Set.copyOf(user.getStores());
        currentStores.forEach(user::removeStore);
        stores.forEach(user::assignStore);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "USER_UPDATED",
                "User",
                user.getId()
        ));

        return UserResponse.from(user);
    }

    public UserResponse updateStatus(UUID id, UserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        String action;
        if (request.active()) {
            user.setActive(true);
            action = "USER_ACTIVATED";
        } else {
            user.setActive(false);
            action = "USER_DEACTIVATED";
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                action,
                "User",
                user.getId()
        ));

        return UserResponse.from(user);
    }

    private void requireRoleAdministrationAuthority(Set<Role> roles, Authentication authentication) {
        Set<String> callerAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        for (Role role : roles) {
            for (String permissionCode : role.permissionCodes()) {
                if (!callerAuthorities.contains(permissionCode)) {
                    throw new ApiException(
                            ErrorCode.ACCESS_DENIED,
                            "Cannot assign a role containing permissions you do not possess (missing: " + permissionCode + ")"
                    );
                }
            }
        }
    }
    
    private void requireStoreAdministrationAuthority(Set<Store> stores, UUID adminId) {
        for (Store store : stores) {
            if (!userRepository.hasStoreAccess(adminId, store.getId())) {
                throw new ApiException(
                        ErrorCode.ACCESS_DENIED,
                        "Cannot assign a user to a store you do not possess access to (storeId: " + store.getId() + ")"
                );
            }
        }
    }
}
