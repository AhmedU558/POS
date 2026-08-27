package com.pos.organization.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.organization.domain.Store;
import com.pos.organization.dto.StoreRequest;
import com.pos.organization.dto.StoreResponse;
import com.pos.organization.dto.StoreStatusRequest;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    public StoreService(StoreRepository storeRepository, UserRepository userRepository, AuditRecorder auditRecorder) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> listStores(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStores().stream()
                        .map(StoreResponse::from)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public StoreResponse getStore(UUID id) {
        return storeRepository.findById(id)
                .map(StoreResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));
    }

    public StoreResponse createStore(StoreRequest request) {
        Store store = new Store(request.code(), request.name(), request.currencyCode(), request.timezone());
        Store savedStore = storeRepository.save(store);

        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        // Also add the store to the creating user, otherwise they cant access it
        userRepository.findById(userDetails.getId()).ifPresent(user -> {
            user.assignStore(savedStore);
            userRepository.save(user);
        });

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "STORE_CREATED",
                "Store",
                savedStore.getId()
        ));

        return StoreResponse.from(savedStore);
    }

    public StoreResponse updateStore(UUID id, StoreRequest request) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        store.setCode(request.code());
        store.setName(request.name());
        store.setCurrencyCode(request.currencyCode());
        store.setTimezone(request.timezone());

        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "STORE_UPDATED",
                "Store",
                store.getId()
        ));

        return StoreResponse.from(store);
    }

    public StoreResponse updateStatus(UUID id, StoreStatusRequest request) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        String action;
        if (request.active()) {
            store.setActive(true);
            action = "STORE_ACTIVATED";
        } else {
            store.setActive(false);
            action = "STORE_DEACTIVATED";
        }

        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                action,
                "Store",
                store.getId()
        ));

        return StoreResponse.from(store);
    }
}
