package com.pos.organization.controller;

import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.response.ApiResponse;
import com.pos.organization.domain.Store;
import com.pos.organization.domain.StoreFbrConfig;
import com.pos.organization.repository.StoreFbrConfigRepository;
import com.pos.organization.repository.StoreRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/fbr-config")
public class StoreFbrConfigController {

    private final StoreRepository storeRepository;
    private final StoreFbrConfigRepository fbrConfigRepository;

    public StoreFbrConfigController(StoreRepository storeRepository, StoreFbrConfigRepository fbrConfigRepository) {
        this.storeRepository = storeRepository;
        this.fbrConfigRepository = fbrConfigRepository;
    }

    public record FbrConfigResponse(
            boolean enabled,
            String environment,
            String ntn,
            String strn,
            String posId,
            boolean hasSecret
    ) {
        public static FbrConfigResponse fromEntity(StoreFbrConfig config) {
            return new FbrConfigResponse(
                    config.isEnabled(),
                    config.getEnvironment(),
                    config.getNtn(),
                    config.getStrn(),
                    config.getPosId(),
                    config.getEncryptedSecret() != null && !config.getEncryptedSecret().isBlank()
            );
        }
    }

    public record FbrConfigUpdateRequest(
            @NotNull Boolean enabled,
            @NotBlank String environment,
            String ntn,
            String strn,
            String posId,
            String secret
    ) {}

    @GetMapping
    @PreAuthorize("hasAuthority('STORE_WRITE')")
    @Transactional(readOnly = true)
    public ApiResponse<FbrConfigResponse> getFbrConfig(@PathVariable UUID storeId) {
        StoreFbrConfig config = fbrConfigRepository.findById(storeId).orElse(null);
        if (config == null) {
            return ApiResponse.of(new FbrConfigResponse(false, "sandbox", "", "", "", false), com.pos.common.config.RequestCorrelation.currentId());
        }
        return ApiResponse.of(FbrConfigResponse.fromEntity(config), com.pos.common.config.RequestCorrelation.currentId());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('STORE_WRITE')")
    @Transactional
    public ApiResponse<FbrConfigResponse> updateFbrConfig(
            @PathVariable UUID storeId,
            @Valid @RequestBody FbrConfigUpdateRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        StoreFbrConfig config = fbrConfigRepository.findById(storeId).orElseGet(() -> new StoreFbrConfig(store));
        
        config.setEnabled(request.enabled());
        config.setEnvironment(request.environment());
        config.setNtn(request.ntn());
        config.setStrn(request.strn());
        config.setPosId(request.posId());
        
        // Only update secret if provided
        if (request.secret() != null && !request.secret().isBlank()) {
            config.setEncryptedSecret(request.secret()); // In a real system, this should be encrypted
        }

        config = fbrConfigRepository.save(config);
        
        return ApiResponse.of(FbrConfigResponse.fromEntity(config), com.pos.common.config.RequestCorrelation.currentId());
    }
}
