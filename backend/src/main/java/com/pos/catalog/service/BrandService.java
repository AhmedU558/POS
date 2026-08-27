package com.pos.catalog.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.catalog.dto.BrandRequest;
import com.pos.catalog.dto.BrandResponse;
import com.pos.catalog.entity.Brand;
import com.pos.catalog.repository.BrandRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BrandService {
    private final BrandRepository brandRepository;
    private final AuditRecorder auditRecorder;

    public BrandService(BrandRepository brandRepository, AuditRecorder auditRecorder) {
        this.brandRepository = brandRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> listBrands() {
        return brandRepository.findAll().stream()
                .map(BrandResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        if (brandRepository.existsByName(request.name())) {
            throw new ApiException(ErrorCode.CONFLICT, "Brand with this name already exists.");
        }
        Brand brand = new Brand();
        brand.setName(request.name());
        brand.setDescription(request.description());
        if (request.isActive() != null) {
            brand.setActive(request.isActive());
        }

        Brand saved = brandRepository.save(brand);

        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "BRAND_CREATED",
                "Brand",
                saved.getId()
        ));
        return BrandResponse.from(saved);
    }

    @Transactional
    public BrandResponse updateBrand(UUID id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Brand not found."));

        if (brandRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Brand with this name already exists.");
        }
        brand.setName(request.name());
        brand.setDescription(request.description());
        if (request.isActive() != null) {
            brand.setActive(request.isActive());
        }

        Brand saved = brandRepository.save(brand);

        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "BRAND_UPDATED",
                "Brand",
                saved.getId()
        ));
        return BrandResponse.from(saved);
    }
}
