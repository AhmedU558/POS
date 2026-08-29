package com.pos.inventory.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.inventory.domain.InventoryBalance;
import com.pos.inventory.domain.InventoryTransaction;
import com.pos.inventory.domain.TransactionType;
import com.pos.inventory.dto.InventoryAdjustmentRequest;
import com.pos.inventory.dto.InventoryBalanceResponse;
import com.pos.inventory.dto.InventoryTransactionResponse;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.inventory.repository.InventoryTransactionRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final AuditRecorder auditRecorder;

    public InventoryService(InventoryBalanceRepository balanceRepository,
                            InventoryTransactionRepository transactionRepository,
                            ProductRepository productRepository,
                            StoreRepository storeRepository,
                            UserRepository userRepository,
                            StoreScopeEvaluator storeScopeEvaluator,
                            AuditRecorder auditRecorder) {
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<InventoryBalanceResponse> searchBalances(UUID storeId, UUID categoryId, String query, Pageable pageable) {
        if (storeId == null) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "storeId is required");
        }
        if (!storeScopeEvaluator.canAccess(storeId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return balanceRepository.searchBalances(storeId, categoryId, query, pageable)
                .map(InventoryBalanceResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public InventoryBalanceResponse getBalance(UUID storeId, UUID productId) {
        if (!storeScopeEvaluator.canAccess(storeId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        InventoryBalance balance = balanceRepository.findByProductIdAndStoreIdForUpdate(productId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Balance not found"));
        return InventoryBalanceResponse.fromEntity(balance);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> getMovements(UUID storeId, UUID productId, Pageable pageable) {
        if (!storeScopeEvaluator.canAccess(storeId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return transactionRepository.findByProductIdAndStoreIdOrderByCreatedAtDesc(productId, storeId, pageable)
                .map(InventoryTransactionResponse::fromEntity);
    }

    @Transactional
    public InventoryBalanceResponse adjustStock(InventoryAdjustmentRequest request) {
        if (!storeScopeEvaluator.canAccess(request.storeId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        if (request.quantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Adjustment quantity cannot be zero");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        User currentUser = getCurrentUser();

        // Pessimistic lock for atomic update
        InventoryBalance balance = balanceRepository.findByProductIdAndStoreIdForUpdate(product.getId(), store.getId())
                .orElseGet(() -> balanceRepository.save(new InventoryBalance(product, store)));

        balance.addQuantity(request.quantity());

        if (balance.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Adjustment would result in negative stock");
        }

        balanceRepository.save(balance);

        InventoryTransaction tx = new InventoryTransaction(
                product,
                store,
                TransactionType.ADJUSTMENT,
                request.quantity(),
                request.reason(),
                currentUser
        );
        transactionRepository.save(tx);

        AuditActor actor = currentUser != null ? AuditActor.user(currentUser.getId()) : AuditActor.system();
        AuditEvent event = AuditEvent.of(actor, "STOCK_ADJUSTMENT", "InventoryTransaction", tx.getId());
        auditRecorder.record(event);

        return InventoryBalanceResponse.fromEntity(balance);
    }

    private User getCurrentUser() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) return null;
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.equals("anonymousUser")) return null;
        return userRepository.findByUsername(username).orElse(null);
    }
}