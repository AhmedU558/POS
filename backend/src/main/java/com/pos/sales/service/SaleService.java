package com.pos.sales.service;

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
import com.pos.inventory.service.InventoryService;
import com.pos.organization.domain.Register;
import com.pos.organization.domain.RegisterSession;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.RegisterSessionRepository;
import com.pos.sales.domain.CashTransaction;
import com.pos.sales.domain.IdempotencyKey;
import com.pos.sales.domain.PaymentMethod;
import com.pos.sales.domain.Sale;
import com.pos.sales.domain.SaleItem;
import com.pos.sales.domain.SalePayment;
import com.pos.sales.dto.SaleCreateRequest;
import com.pos.sales.dto.SaleItemRequest;
import com.pos.sales.dto.SalePaymentRequest;
import com.pos.sales.dto.SaleResponse;
import com.pos.sales.repository.CashTransactionRepository;
import com.pos.sales.repository.IdempotencyKeyRepository;
import com.pos.sales.repository.PaymentMethodRepository;
import com.pos.sales.repository.SaleRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Year;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class SaleService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final SaleRepository saleRepository;
    private final RegisterSessionRepository sessionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final InventoryService inventoryService;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    public SaleService(
            SaleRepository saleRepository,
            RegisterSessionRepository sessionRepository,
            PaymentMethodRepository paymentMethodRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            CashTransactionRepository cashTransactionRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            InventoryService inventoryService,
            StoreScopeEvaluator storeScopeEvaluator,
            UserRepository userRepository,
            AuditRecorder auditRecorder) {
        this.saleRepository = saleRepository;
        this.sessionRepository = sessionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.inventoryService = inventoryService;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        Sale sale = saleRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Sale not found"));
        if (!storeScopeEvaluator.canAccess(sale.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return SaleResponse.fromEntity(sale);
    }

    @Transactional
    public SaleResponse create(SaleCreateRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key is required");
        }
        String hash = requestHash(request);
        IdempotencyKey existing = idempotencyKeyRepository.findByKey(idempotencyKey.trim()).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash)) {
                throw new ApiException(ErrorCode.DUPLICATE_REQUEST, "Idempotency key was reused with a different request");
            }
            if (existing.getSale() != null) {
                return get(existing.getSale().getId());
            }
        }

        if (!storeScopeEvaluator.canAccess(request.storeId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        RegisterSession session = sessionRepository.findByIdForUpdate(request.registerSessionId())
                .orElseThrow(() -> new ApiException(ErrorCode.REGISTER_SESSION_REQUIRED, "Register session not found"));
        if (!session.isOpen()) {
            throw new ApiException(ErrorCode.REGISTER_SESSION_REQUIRED, "Register session is not open");
        }
        Register register = session.getRegister();
        Store store = register.getStore();
        if (!store.getId().equals(request.storeId())
                || !register.getId().equals(request.registerId())
                || !register.getTerminal().getId().equals(request.terminalId())) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Sale context does not match the open register session");
        }

        User cashier = currentUser();
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Customer not found"));
        }

        SalePaymentRequest paymentRequest = request.payments().getFirst();
        PaymentMethod method = paymentMethodRepository.findById(paymentRequest.paymentMethodId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Payment method not found"));
        if (!method.isActive() || !PaymentMethod.CASH.equals(method.getCode())) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only a single CASH payment is allowed");
        }

        Sale sale = new Sale();
        sale.setStore(store);
        sale.setTerminal(register.getTerminal());
        sale.setRegister(register);
        sale.setRegisterSession(session);
        sale.setCashier(cashier);
        sale.setCustomer(customer);
        sale.setStatus(Sale.STATUS_COMPLETED);
        sale.setDiscountTotal(ZERO);
        sale.setCurrencyCode(store.getCurrencyCode());
        sale.setReceiptNumber(nextReceiptNumber());

        BigDecimal subtotal = ZERO;
        BigDecimal taxTotal = ZERO;
        for (SaleItemRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
            if (!product.isActive()) {
                throw new ApiException(ErrorCode.RESOURCE_INACTIVE, "Product is inactive");
            }
            BigDecimal unitPrice = money(product.getSellingPrice());
            BigDecimal lineSubtotal = money(unitPrice.multiply(line.quantity()));
            BigDecimal taxAmount = money(lineSubtotal.multiply(product.getTaxRate()));
            BigDecimal lineTotal = money(lineSubtotal.add(taxAmount));

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setDiscountAmount(ZERO);
            item.setTaxAmount(taxAmount);
            item.setLineTotal(lineTotal);
            sale.addItem(item);

            subtotal = subtotal.add(lineSubtotal);
            taxTotal = taxTotal.add(taxAmount);
        }

        BigDecimal grandTotal = money(subtotal.add(taxTotal));

        sale.setSubtotal(subtotal);
        sale.setTaxTotal(taxTotal);
        sale.setGrandTotal(grandTotal);

        SalePayment payment = new SalePayment();
        payment.setPaymentMethod(method);
        payment.setAmount(grandTotal);
        payment.setStatus(SalePayment.STATUS_COMPLETED);
        sale.addPayment(payment);

        Sale saved = saleRepository.save(sale);

        for (SaleItem item : saved.getItems()) {
            inventoryService.deductForSale(
                    store.getId(),
                    item.getProduct().getId(),
                    item.getQuantity(),
                    saved.getId());
        }

        CashTransaction cash = new CashTransaction();
        cash.setRegisterSession(session);
        cash.setTransactionType(CashTransaction.TYPE_SALE);
        cash.setAmount(saved.getGrandTotal());
        cash.setReferenceType("Sale");
        cash.setReferenceId(saved.getId());
        cash.setCreatedBy(cashier);
        cashTransactionRepository.save(cash);

        persistIdempotency(idempotencyKey.trim(), hash, saved, existing);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(cashier.getId()),
                "SALE_CREATED",
                "Sale",
                saved.getId()));

        return SaleResponse.fromEntity(saleRepository.findDetailedById(saved.getId()).orElse(saved));
    }

    private void persistIdempotency(String key, String hash, Sale sale, IdempotencyKey existing) {
        try {
            IdempotencyKey record = existing == null ? new IdempotencyKey() : existing;
            record.setKey(key);
            record.setRequestHash(hash);
            record.setSale(sale);
            idempotencyKeyRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            IdempotencyKey raced = idempotencyKeyRepository.findByKey(key)
                    .orElseThrow(() -> new ApiException(ErrorCode.DUPLICATE_REQUEST, "Duplicate sale request"));
            if (!raced.getRequestHash().equals(hash)) {
                throw new ApiException(ErrorCode.DUPLICATE_REQUEST, "Idempotency key was reused with a different request");
            }
            if (raced.getSale() == null) {
                throw new ApiException(ErrorCode.DUPLICATE_REQUEST, "Duplicate sale request");
            }
        }
    }

    private String nextReceiptNumber() {
        return "R-" + Year.now() + "-" + String.format("%06d", saleRepository.nextReceiptSequence());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static String requestHash(SaleCreateRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = request.storeId()
                    + "|" + request.terminalId()
                    + "|" + request.registerId()
                    + "|" + request.registerSessionId()
                    + "|" + request.customerId()
                    + "|" + request.items()
                    + "|" + request.payments();
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Unable to hash idempotency request");
        }
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));
    }
}
