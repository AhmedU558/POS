package com.pos.purchases.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.purchases.domain.PurchaseOrder;
import com.pos.purchases.domain.PurchaseOrderItem;
import com.pos.purchases.domain.PurchaseOrderStatus;
import com.pos.purchases.dto.PurchaseOrderItemRequest;
import com.pos.purchases.dto.PurchaseOrderResponse;
import com.pos.purchases.dto.PurchaseOrderWriteRequest;
import com.pos.purchases.repository.PurchaseOrderRepository;
import com.pos.suppliers.domain.Supplier;
import com.pos.suppliers.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final AuditRecorder auditRecorder;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            AuditRecorder auditRecorder) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> search(String query, PurchaseOrderStatus status, Pageable pageable) {
        return purchaseOrderRepository.search(query == null ? "" : query.trim(), status, pageable)
                .map(PurchaseOrderResponse::fromEntityWithoutItems);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(UUID id) {
        return PurchaseOrderResponse.fromEntity(requireDetailed(id));
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderWriteRequest request) {
        if (purchaseOrderRepository.existsByPoNumber(request.poNumber().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Purchase order number already exists");
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(PurchaseOrderStatus.DRAFT);
        applyDraft(order, request);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        audit("PURCHASE_ORDER_CREATED", saved.getId());
        return PurchaseOrderResponse.fromEntity(requireDetailed(saved.getId()));
    }

    @Transactional
    public PurchaseOrderResponse update(UUID id, PurchaseOrderWriteRequest request) {
        PurchaseOrder order = requireDetailed(id);
        requireDraft(order);
        if (purchaseOrderRepository.existsByPoNumberAndIdNot(request.poNumber().trim(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Purchase order number already exists");
        }
        applyDraft(order, request);
        purchaseOrderRepository.save(order);
        audit("PURCHASE_ORDER_UPDATED", order.getId());
        return PurchaseOrderResponse.fromEntity(requireDetailed(id));
    }

    @Transactional
    public PurchaseOrderResponse submit(UUID id) {
        PurchaseOrder order = requireDetailed(id);
        requireDraft(order);
        order.setStatus(PurchaseOrderStatus.SUBMITTED);
        purchaseOrderRepository.save(order);
        audit("PURCHASE_ORDER_SUBMITTED", order.getId());
        return PurchaseOrderResponse.fromEntity(order);
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder order = requireDetailed(id);
        requireDraft(order);
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        purchaseOrderRepository.save(order);
        audit("PURCHASE_ORDER_CANCELLED", order.getId());
        return PurchaseOrderResponse.fromEntity(order);
    }

    private void applyDraft(PurchaseOrder order, PurchaseOrderWriteRequest request) {
        order.setPoNumber(request.poNumber().trim());
        order.setSupplier(requireSupplier(request.supplierId()));
        order.setNotes(blankToNull(request.notes()));
        order.replaceItems(buildItems(order, request.items()));
    }

    private List<PurchaseOrderItem> buildItems(PurchaseOrder order, List<PurchaseOrderItemRequest> items) {
        List<UUID> productIds = items.stream().map(PurchaseOrderItemRequest::productId).toList();
        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<PurchaseOrderItem> built = new ArrayList<>();
        for (PurchaseOrderItemRequest item : items) {
            Product product = products.get(item.productId());
            if (product == null) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found");
            }
            built.add(new PurchaseOrderItem(order, product, item.quantity()));
        }
        return built;
    }

    private PurchaseOrder requireDetailed(UUID id) {
        return purchaseOrderRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Purchase order not found"));
    }

    private Supplier requireSupplier(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier not found"));
    }

    private static void requireDraft(PurchaseOrder order) {
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only draft purchase orders can be changed");
        }
    }

    private void audit(String action, UUID id) {
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                action,
                "PurchaseOrder",
                id));
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
