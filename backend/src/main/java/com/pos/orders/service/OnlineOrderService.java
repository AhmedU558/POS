package com.pos.orders.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.customers.domain.Customer;
import com.pos.customers.repository.CustomerRepository;
import com.pos.orders.domain.OnlineOrder;
import com.pos.orders.domain.OnlineOrderItem;
import com.pos.orders.dto.OnlineOrderItemRequest;
import com.pos.orders.dto.OnlineOrderRequest;
import com.pos.orders.dto.OnlineOrderResponse;
import com.pos.orders.repository.OnlineOrderRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class OnlineOrderService {

    private final OnlineOrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;
    private final com.pos.inventory.service.InventoryService inventoryService;

    public OnlineOrderService(
            OnlineOrderRepository orderRepository,
            StoreRepository storeRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            StoreScopeEvaluator storeScopeEvaluator,
            UserRepository userRepository,
            AuditRecorder auditRecorder,
            com.pos.inventory.service.InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public Page<OnlineOrderResponse> list(Pageable pageable) {
        var storeIds = storeScopeEvaluator.permittedStoreIds();
        if (storeIds.isEmpty()) return Page.empty(pageable);
        return orderRepository.search(storeIds, pageable).map(OnlineOrderResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public OnlineOrderResponse get(UUID id) {
        return OnlineOrderResponse.fromEntity(requireAccessible(id));
    }

    private OnlineOrder requireAccessible(UUID id) {
        OnlineOrder order = orderRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Online order not found"));
        if (!storeScopeEvaluator.canAccess(order.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return order;
    }

    @Transactional
    public OnlineOrderResponse create(OnlineOrderRequest request, String idempotencyKey) {
        if (!storeScopeEvaluator.canAccess(request.storeId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        Optional<OnlineOrder> existing = orderRepository.findByChannelAndExternalOrderId(request.channel(), request.externalOrderId());
        if (existing.isPresent()) {
            return OnlineOrderResponse.fromEntity(existing.get()); // Idempotent return
        }

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Customer not found"));
        }

        OnlineOrder order = new OnlineOrder();
        order.setStore(store);
        order.setCustomer(customer);
        order.setChannel(request.channel());
        order.setExternalOrderId(request.externalOrderId());
        order.setSubtotal(request.subtotal());
        order.setDiscountTotal(request.discountTotal());
        order.setTaxTotal(request.taxTotal());
        order.setGrandTotal(request.grandTotal());
        order.setCurrencyCode(request.currencyCode());
        order.setNotes(request.notes());

        for (OnlineOrderItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
            
            OnlineOrderItem item = new OnlineOrderItem();
            item.setProduct(product);
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(itemReq.unitPrice());
            item.setDiscountAmount(itemReq.discountAmount());
            item.setTaxAmount(itemReq.taxAmount());
            item.setLineTotal(itemReq.lineTotal());
            
            order.addItem(item);
        }

        OnlineOrder saved = orderRepository.save(order);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "ONLINE_ORDER_CREATED",
                "OnlineOrder",
                saved.getId()));

        return OnlineOrderResponse.fromEntity(saved);
    }

    @Transactional
    public void confirm(UUID id) {
        OnlineOrder order = requireAccessible(id);
        if (!order.getStatus().equals(OnlineOrder.STATUS_PENDING)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only pending orders can be confirmed");
        }
        order.setStatus(OnlineOrder.STATUS_CONFIRMED);
        
        // Reserve inventory on confirm
        for (OnlineOrderItem item : order.getItems()) {
            inventoryService.deductForOnlineOrder(order.getStore().getId(), item.getProduct().getId(), item.getQuantity(), order.getId());
        }
        
        orderRepository.save(order);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "ONLINE_ORDER_CONFIRMED",
                "OnlineOrder",
                order.getId()));
    }

    @Transactional
    public void cancel(UUID id) {
        OnlineOrder order = requireAccessible(id);
        if (order.getStatus().equals(OnlineOrder.STATUS_FULFILLED) || order.getStatus().equals(OnlineOrder.STATUS_REFUNDED)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot cancel a fulfilled or refunded order");
        }
        
        // If it was confirmed, we need to release the reserved inventory
        if (order.getStatus().equals(OnlineOrder.STATUS_CONFIRMED)) {
            for (OnlineOrderItem item : order.getItems()) {
                inventoryService.restoreForOnlineOrder(order.getStore().getId(), item.getProduct().getId(), item.getQuantity(), order.getId());
            }
        }
        
        order.setStatus(OnlineOrder.STATUS_CANCELLED);
        orderRepository.save(order);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "ONLINE_ORDER_CANCELLED",
                "OnlineOrder",
                order.getId()));
    }

    @Transactional
    public void fulfill(UUID id) {
        OnlineOrder order = requireAccessible(id);
        if (!order.getStatus().equals(OnlineOrder.STATUS_CONFIRMED)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only confirmed orders can be fulfilled");
        }
        order.setStatus(OnlineOrder.STATUS_FULFILLED);
        orderRepository.save(order);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "ONLINE_ORDER_FULFILLED",
                "OnlineOrder",
                order.getId()));
    }

    @Transactional
    public void refund(UUID id) {
        OnlineOrder order = requireAccessible(id);
        if (!order.getStatus().equals(OnlineOrder.STATUS_FULFILLED)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only fulfilled orders can be refunded");
        }
        order.setStatus(OnlineOrder.STATUS_REFUNDED);
        
        // Return inventory
        for (OnlineOrderItem item : order.getItems()) {
            inventoryService.restoreForOnlineOrder(order.getStore().getId(), item.getProduct().getId(), item.getQuantity(), order.getId());
        }
        
        orderRepository.save(order);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "ONLINE_ORDER_REFUNDED",
                "OnlineOrder",
                order.getId()));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));
    }
}
