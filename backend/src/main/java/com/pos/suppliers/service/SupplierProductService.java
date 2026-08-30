package com.pos.suppliers.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.suppliers.domain.Supplier;
import com.pos.suppliers.domain.SupplierProduct;
import com.pos.suppliers.dto.SupplierProductResponse;
import com.pos.suppliers.dto.SupplierProductsReplaceRequest;
import com.pos.suppliers.repository.SupplierProductRepository;
import com.pos.suppliers.repository.SupplierRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SupplierProductService {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ProductRepository productRepository;
    private final AuditRecorder auditRecorder;

    public SupplierProductService(
            SupplierRepository supplierRepository,
            SupplierProductRepository supplierProductRepository,
            ProductRepository productRepository,
            AuditRecorder auditRecorder) {
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.productRepository = productRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<SupplierProductResponse> list(UUID supplierId) {
        requireSupplier(supplierId);
        return supplierProductRepository.findBySupplierIdOrderByProductSkuAsc(supplierId).stream()
                .map(SupplierProductResponse::fromEntity)
                .toList();
    }

    @Transactional
    public List<SupplierProductResponse> replace(UUID supplierId, SupplierProductsReplaceRequest request) {
        Supplier supplier = requireSupplier(supplierId);
        List<UUID> productIds = new ArrayList<>(new LinkedHashSet<>(request.productIds()));

        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (UUID productId : productIds) {
            if (!products.containsKey(productId)) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found");
            }
        }

        supplierProductRepository.deleteBySupplierId(supplierId);
        supplierProductRepository.flush();
        for (UUID productId : productIds) {
            supplierProductRepository.save(new SupplierProduct(supplier, products.get(productId)));
        }
        supplierProductRepository.flush();

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                "SUPPLIER_PRODUCTS_UPDATED",
                "Supplier",
                supplier.getId()));

        return list(supplierId);
    }

    private Supplier requireSupplier(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier not found"));
    }

    private UUID currentUserId() {
        CustomUserDetails user = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return user.getId();
    }
}
