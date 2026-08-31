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
import com.pos.inventory.domain.InventoryBatch;
import com.pos.inventory.domain.InventoryTransaction;
import com.pos.inventory.domain.StockAlert;
import com.pos.inventory.domain.TransactionType;
import com.pos.inventory.dto.InventoryAdjustmentRequest;
import com.pos.inventory.dto.InventoryBalanceResponse;
import com.pos.inventory.dto.InventoryBatchResponse;
import com.pos.inventory.dto.InventoryReceiptRequest;
import com.pos.inventory.dto.InventoryReportRow;
import com.pos.inventory.dto.InventoryTransactionResponse;
import com.pos.inventory.dto.StockAlertResponse;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.inventory.repository.InventoryBatchRepository;
import com.pos.inventory.repository.InventoryTransactionRepository;
import com.pos.inventory.repository.StockAlertRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryService {

    static final int DEFAULT_EXPIRY_WINDOW_DAYS = 7;

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StockAlertRepository alertRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final AuditRecorder auditRecorder;

    public InventoryService(InventoryBalanceRepository balanceRepository,
                            InventoryBatchRepository batchRepository,
                            InventoryTransactionRepository transactionRepository,
                            StockAlertRepository alertRepository,
                            ProductRepository productRepository,
                            StoreRepository storeRepository,
                            UserRepository userRepository,
                            StoreScopeEvaluator storeScopeEvaluator,
                            AuditRecorder auditRecorder) {
        this.balanceRepository = balanceRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
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

    @Transactional(readOnly = true)
    public Page<InventoryBatchResponse> listBatches(UUID storeId, UUID productId, Integer days, Pageable pageable) {
        Store store = requireReadableStore(storeId);
        int window = normalizeExpiryWindow(days);
        LocalDate today = todayInStore(store);
        return batchRepository.searchBatches(storeId, productId, pageable)
                .map(batch -> InventoryBatchResponse.fromEntity(batch, today, window));
    }

    @Transactional(readOnly = true)
    public Page<InventoryBatchResponse> listExpiry(UUID storeId, Integer days, Pageable pageable) {
        Store store = requireReadableStore(storeId);
        int window = normalizeExpiryWindow(days);
        LocalDate today = todayInStore(store);
        return batchRepository.findExpiringOnOrBefore(storeId, today.plusDays(window), pageable)
                .map(batch -> InventoryBatchResponse.fromEntity(batch, today, window));
    }

    @Transactional
    public Page<StockAlertResponse> listAlerts(UUID storeId, String alertType, String status, Integer days, Pageable pageable) {
        Store store = requireReadableStore(storeId);
        int window = normalizeExpiryWindow(days);
        LocalDate today = todayInStore(store);
        refreshAlerts(store, today, window);
        return alertRepository.search(storeId, blankToNull(alertType), blankToNull(status), pageable)
                .map(alert -> StockAlertResponse.fromEntity(alert, today));
    }

    @Transactional
    public StockAlertResponse acknowledgeAlert(UUID alertId) {
        StockAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Alert not found"));
        if (!storeScopeEvaluator.canAccess(alert.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        User currentUser = getCurrentUser();
        if (!StockAlert.STATUS_ACKNOWLEDGED.equals(alert.getStatus())) {
            alert.acknowledge(currentUser);
            alertRepository.save(alert);
            AuditActor actor = currentUser != null ? AuditActor.user(currentUser.getId()) : AuditActor.system();
            auditRecorder.record(AuditEvent.of(actor, "ALERT_ACKNOWLEDGE", "StockAlert", alert.getId()));
        }
        return StockAlertResponse.fromEntity(alert, todayInStore(alert.getStore()));
    }

    @Transactional(readOnly = true)
    public Page<InventoryReportRow> reportInventory(UUID storeId, boolean lowStockOnly, Pageable pageable) {
        requireReadableStore(storeId);
        return balanceRepository.searchReportBalances(storeId, lowStockOnly, pageable)
                .map(InventoryReportRow::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> reportMovements(UUID storeId, UUID productId, Pageable pageable) {
        requireReadableStore(storeId);
        if (productId != null) {
            return transactionRepository.findByProductIdAndStoreIdOrderByCreatedAtDesc(productId, storeId, pageable)
                    .map(InventoryTransactionResponse::fromEntity);
        }
        return transactionRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable)
                .map(InventoryTransactionResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<InventoryBatchResponse> reportExpiry(UUID storeId, Integer days, Pageable pageable) {
        return listExpiry(storeId, days, pageable);
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

        syncLowStockAlert(store, product, balance.getQuantity());

        return InventoryBalanceResponse.fromEntity(balance);
    }

    @Transactional
    public void deductForSale(UUID storeId, UUID productId, BigDecimal quantity, UUID saleId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        User currentUser = getCurrentUser();

        InventoryBalance balance = balanceRepository
                .findByProductIdAndStoreIdForUpdate(product.getId(), store.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INSUFFICIENT_STOCK, "Required stock is unavailable"));
        if (balance.getQuantity().compareTo(quantity) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK, "Required stock is unavailable");
        }

        balance.addQuantity(quantity.negate());
        balanceRepository.save(balance);

        InventoryTransaction sale = new InventoryTransaction(
                product,
                store,
                TransactionType.SALE,
                quantity.negate(),
                null,
                currentUser
        );
        sale.assignReference("Sale", saleId);
        transactionRepository.save(sale);
        syncLowStockAlert(store, product, balance.getQuantity());
    }

    @Transactional
    public void addForReturn(UUID storeId, UUID productId, BigDecimal quantity, UUID saleReturnId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        User currentUser = getCurrentUser();

        InventoryBalance balance = balanceRepository
                .findByProductIdAndStoreIdForUpdate(product.getId(), store.getId())
                .orElseGet(() -> {
                    return new InventoryBalance(product, store);
                });

        balance.addQuantity(quantity);
        balanceRepository.save(balance);

        InventoryTransaction ret = new InventoryTransaction(
                product,
                store,
                TransactionType.RETURN,
                quantity,
                null,
                currentUser
        );
        ret.assignReference("SaleReturn", saleReturnId);
        transactionRepository.save(ret);
        syncLowStockAlert(store, product, balance.getQuantity());
    }

    @Transactional
    public InventoryBalanceResponse receiveStock(InventoryReceiptRequest request) {
        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));
        if (!store.isActive()) {
            throw new ApiException(ErrorCode.RESOURCE_INACTIVE, "Store is inactive");
        }
        if (!storeScopeEvaluator.canAccess(store.getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        if (!product.isActive()) {
            throw new ApiException(ErrorCode.RESOURCE_INACTIVE, "Product is inactive");
        }

        User currentUser = getCurrentUser();

        InventoryBatch batch = resolveReceiptBatch(product, store, request);

        balanceRepository.insertZeroBalanceIfAbsent(product.getId(), store.getId());
        InventoryBalance balance = balanceRepository
                .findByProductIdAndStoreIdForUpdate(product.getId(), store.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Balance could not be created"));

        BigDecimal quantityBefore = balance.getQuantity();
        balance.addQuantity(request.quantity());
        balanceRepository.save(balance);

        InventoryTransaction receipt = new InventoryTransaction(
                product,
                store,
                TransactionType.RECEIPT,
                request.quantity(),
                null,
                currentUser
        );
        if (batch != null) {
            receipt.assignBatch(batch.getId());
        }
        transactionRepository.save(receipt);

        AuditActor actor = currentUser != null ? AuditActor.user(currentUser.getId()) : AuditActor.system();
        AuditEvent event = new AuditEvent(
                actor,
                "STOCK_RECEIPT",
                "InventoryTransaction",
                receipt.getId(),
                quantitySnapshot(quantityBefore),
                quantitySnapshot(balance.getQuantity()),
                null
        );
        auditRecorder.record(event);

        syncLowStockAlert(store, product, balance.getQuantity());
        if (batch != null) {
            syncExpiryAlert(store, todayInStore(store), DEFAULT_EXPIRY_WINDOW_DAYS, batch);
        }

        return InventoryBalanceResponse.fromEntity(balance);
    }

    private InventoryBatch resolveReceiptBatch(Product product, Store store, InventoryReceiptRequest request) {
        if (!product.isTrackBatch() && !product.isTrackExpiry()) {
            return null;
        }

        String batchNumber = normalizeBatchNumber(request.batchNumber());
        if (batchNumber == null) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Batch number is required for this product");
        }
        if (product.isTrackExpiry() && request.expirationDate() == null) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Expiration date is required for this product");
        }

        batchRepository.insertZeroBatchIfAbsent(
                product.getId(),
                store.getId(),
                batchNumber,
                request.expirationDate(),
                request.manufacturingDate());
        InventoryBatch batch = batchRepository
                .findByProductIdAndStoreIdAndBatchNumberForUpdate(product.getId(), store.getId(), batchNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Batch could not be created"));

        if (datesConflict(batch.getExpirationDate(), request.expirationDate())) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Expiration date does not match the existing batch");
        }
        if (datesConflict(batch.getManufacturingDate(), request.manufacturingDate())) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Manufacturing date does not match the existing batch");
        }
        if (batch.getExpirationDate() == null && request.expirationDate() != null) {
            batch.setExpirationDate(request.expirationDate());
        }
        if (batch.getManufacturingDate() == null && request.manufacturingDate() != null) {
            batch.setManufacturingDate(request.manufacturingDate());
        }

        batch.addQuantity(request.quantity());
        return batchRepository.save(batch);
    }

    private Store requireReadableStore(UUID storeId) {
        if (storeId == null) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "storeId is required");
        }
        if (!storeScopeEvaluator.canAccess(storeId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));
    }

    private static int normalizeExpiryWindow(Integer days) {
        if (days == null) {
            return DEFAULT_EXPIRY_WINDOW_DAYS;
        }
        if (days < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "days must be greater than or equal to zero");
        }
        return days;
    }

    private static LocalDate todayInStore(Store store) {
        try {
            return LocalDate.now(ZoneId.of(store.getTimezone()));
        } catch (DateTimeException ex) {
            return LocalDate.now(ZoneOffset.UTC);
        }
    }

    private static String normalizeBatchNumber(String batchNumber) {
        if (batchNumber == null) {
            return null;
        }
        String trimmed = batchNumber.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean datesConflict(LocalDate stored, LocalDate requested) {
        return stored != null && requested != null && !stored.equals(requested);
    }

    private void refreshAlerts(Store store, LocalDate today, int windowDays) {
        Set<UUID> lowProductIds = new HashSet<>();
        for (InventoryBalance balance : balanceRepository.findBelowMinimum(store.getId())) {
            lowProductIds.add(balance.getProduct().getId());
            upsertLowStock(store, balance.getProduct(), balance.getQuantity());
        }
        for (StockAlert existing : alertRepository.findByStoreIdAndAlertType(store.getId(), StockAlert.TYPE_LOW_STOCK)) {
            if (!lowProductIds.contains(existing.getProduct().getId())) {
                alertRepository.delete(existing);
            }
        }

        LocalDate horizon = today.plusDays(windowDays);
        Set<UUID> expiringBatchIds = new HashSet<>();
        for (InventoryBatch batch : batchRepository.listExpiringOnOrBefore(store.getId(), horizon)) {
            expiringBatchIds.add(batch.getId());
            syncExpiryAlert(store, today, windowDays, batch);
        }
        for (StockAlert existing : alertRepository.findByStoreIdAndAlertType(store.getId(), StockAlert.TYPE_EXPIRY)) {
            UUID batchId = existing.getBatch() == null ? null : existing.getBatch().getId();
            if (batchId == null || !expiringBatchIds.contains(batchId)) {
                alertRepository.delete(existing);
            }
        }
    }

    private void syncLowStockAlert(Store store, Product product, BigDecimal quantity) {
        BigDecimal minimum = product.getMinStock();
        if (minimum != null && quantity.compareTo(minimum) <= 0) {
            upsertLowStock(store, product, quantity);
            return;
        }
        alertRepository.findByStoreIdAndProductIdAndAlertTypeAndBatchIsNull(
                        store.getId(), product.getId(), StockAlert.TYPE_LOW_STOCK)
                .ifPresent(alertRepository::delete);
    }

    private void upsertLowStock(Store store, Product product, BigDecimal quantity) {
        alertRepository.findByStoreIdAndProductIdAndAlertTypeAndBatchIsNull(
                        store.getId(), product.getId(), StockAlert.TYPE_LOW_STOCK)
                .ifPresentOrElse(
                        existing -> {
                            existing.refresh(quantity, product.getMinStock(), null);
                            alertRepository.save(existing);
                        },
                        () -> saveNewAlert(StockAlert.lowStock(store, product, quantity, product.getMinStock())));
    }

    private void syncExpiryAlert(Store store, LocalDate today, int windowDays, InventoryBatch batch) {
        LocalDate expiration = batch.getExpirationDate();
        if (expiration == null || expiration.isAfter(today.plusDays(windowDays))) {
            if (batch.getId() != null) {
                alertRepository.findByStoreIdAndBatchIdAndAlertType(store.getId(), batch.getId(), StockAlert.TYPE_EXPIRY)
                        .ifPresent(alertRepository::delete);
            }
            return;
        }
        alertRepository.findByStoreIdAndBatchIdAndAlertType(store.getId(), batch.getId(), StockAlert.TYPE_EXPIRY)
                .ifPresentOrElse(
                        existing -> {
                            existing.refresh(batch.getQuantity(), null, expiration);
                            alertRepository.save(existing);
                        },
                        () -> saveNewAlert(StockAlert.expiry(store, batch.getProduct(), batch, batch.getQuantity(), expiration)));
    }

    private void saveNewAlert(StockAlert alert) {
        try {
            alertRepository.saveAndFlush(alert);
        } catch (DataIntegrityViolationException ex) {
            if (StockAlert.TYPE_LOW_STOCK.equals(alert.getAlertType())) {
                alertRepository.findByStoreIdAndProductIdAndAlertTypeAndBatchIsNull(
                                alert.getStore().getId(), alert.getProduct().getId(), StockAlert.TYPE_LOW_STOCK)
                        .ifPresent(existing -> {
                            existing.refresh(alert.getQuantity(), alert.getMinimumLevel(), null);
                            alertRepository.save(existing);
                        });
            } else if (alert.getBatch() != null) {
                alertRepository.findByStoreIdAndBatchIdAndAlertType(
                                alert.getStore().getId(), alert.getBatch().getId(), StockAlert.TYPE_EXPIRY)
                        .ifPresent(existing -> {
                            existing.refresh(alert.getQuantity(), null, alert.getExpirationDate());
                            alertRepository.save(existing);
                        });
            }
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String quantitySnapshot(BigDecimal quantity) {
        return "{\"quantity\":\"" + quantity.toPlainString() + "\"}";
    }

    private User getCurrentUser() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) return null;
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.equals("anonymousUser")) return null;
        return userRepository.findByUsername(username).orElse(null);
    }
}