package com.pos.suppliers.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.suppliers.domain.Supplier;
import com.pos.suppliers.dto.SupplierCreateRequest;
import com.pos.suppliers.dto.SupplierResponse;
import com.pos.suppliers.dto.SupplierUpdateRequest;
import com.pos.suppliers.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final AuditRecorder auditRecorder;

    public SupplierService(SupplierRepository supplierRepository, AuditRecorder auditRecorder) {
        this.supplierRepository = supplierRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(String query, Boolean isActive, Pageable pageable) {
        return supplierRepository.search(blankToNull(query), isActive, pageable).map(SupplierResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return SupplierResponse.fromEntity(requireSupplier(id));
    }

    @Transactional
    public SupplierResponse create(SupplierCreateRequest request) {
        if (supplierRepository.existsBySupplierCode(request.supplierCode().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Supplier code already exists");
        }
        Supplier supplier = new Supplier();
        apply(supplier, request.supplierCode(), request.name(), request.phone(), request.email(), request.address(), request.isActive());
        Supplier saved = supplierRepository.save(supplier);
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                "SUPPLIER_CREATED",
                "Supplier",
                saved.getId()));
        return SupplierResponse.fromEntity(saved);
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierUpdateRequest request) {
        Supplier supplier = requireSupplier(id);
        if (supplierRepository.existsBySupplierCodeAndIdNot(request.supplierCode().trim(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Supplier code already exists");
        }
        apply(supplier, request.supplierCode(), request.name(), request.phone(), request.email(), request.address(), request.isActive());
        Supplier saved = supplierRepository.save(supplier);
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                "SUPPLIER_UPDATED",
                "Supplier",
                saved.getId()));
        return SupplierResponse.fromEntity(saved);
    }

    private Supplier requireSupplier(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier not found"));
    }

    private static void apply(
            Supplier supplier,
            String supplierCode,
            String name,
            String phone,
            String email,
            String address,
            boolean active) {
        supplier.setSupplierCode(supplierCode.trim());
        supplier.setName(name.trim());
        supplier.setPhone(blankToNull(phone));
        supplier.setEmail(blankToNull(email));
        supplier.setAddress(blankToNull(address));
        supplier.setActive(active);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UUID currentUserId() {
        CustomUserDetails user = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return user.getId();
    }
}
