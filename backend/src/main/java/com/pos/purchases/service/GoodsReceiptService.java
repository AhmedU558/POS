package com.pos.purchases.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.inventory.dto.InventoryReceiptRequest;
import com.pos.inventory.service.InventoryService;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.purchases.domain.GoodsReceipt;
import com.pos.purchases.domain.GoodsReceiptItem;
import com.pos.purchases.domain.PurchaseOrder;
import com.pos.purchases.domain.PurchaseOrderItem;
import com.pos.purchases.domain.PurchaseOrderStatus;
import com.pos.purchases.dto.GoodsReceiptCreateRequest;
import com.pos.purchases.dto.GoodsReceiptItemRequest;
import com.pos.purchases.dto.GoodsReceiptResponse;
import com.pos.purchases.repository.GoodsReceiptRepository;
import com.pos.purchases.repository.PurchaseOrderRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoodsReceiptService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final AuditRecorder auditRecorder;

    public GoodsReceiptService(
            GoodsReceiptRepository goodsReceiptRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            StoreRepository storeRepository,
            ProductRepository productRepository,
            InventoryService inventoryService,
            StoreScopeEvaluator storeScopeEvaluator,
            AuditRecorder auditRecorder) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse get(UUID id) {
        GoodsReceipt receipt = requireDetailed(id);
        if (!storeScopeEvaluator.canAccess(receipt.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return GoodsReceiptResponse.fromEntity(receipt);
    }

    @Transactional
    public GoodsReceiptResponse create(GoodsReceiptCreateRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findDetailedById(request.purchaseOrderId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Purchase order not found"));
        if (order.getStatus() != PurchaseOrderStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only submitted purchase orders can be received");
        }

        Set<UUID> orderedProductIds = order.getItems().stream()
                .map(PurchaseOrderItem::getProduct)
                .map(Product::getId)
                .collect(Collectors.toSet());
        for (GoodsReceiptItemRequest item : request.items()) {
            if (!orderedProductIds.contains(item.productId())) {
                throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Product is not on the purchase order");
            }
        }

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        for (GoodsReceiptItemRequest item : request.items()) {
            inventoryService.receiveStock(new InventoryReceiptRequest(
                    request.storeId(),
                    item.productId(),
                    item.quantity(),
                    item.batchNumber(),
                    item.expirationDate(),
                    item.manufacturingDate()));
        }

        GoodsReceipt receipt = new GoodsReceipt(order, store);
        for (GoodsReceiptItemRequest item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
            receipt.addItem(new GoodsReceiptItem(
                    receipt,
                    product,
                    item.quantity(),
                    blankToNull(item.batchNumber()),
                    item.expirationDate(),
                    item.manufacturingDate()));
        }
        GoodsReceipt saved = goodsReceiptRepository.save(receipt);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                "GOODS_RECEIPT_CREATED",
                "GoodsReceipt",
                saved.getId()));

        return GoodsReceiptResponse.fromEntity(requireDetailed(saved.getId()));
    }

    private GoodsReceipt requireDetailed(UUID id) {
        return goodsReceiptRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Goods receipt not found"));
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
