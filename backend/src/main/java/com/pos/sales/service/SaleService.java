package com.pos.sales.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.customers.domain.CreditTransactionType;
import com.pos.customers.domain.Customer;
import com.pos.customers.dto.CustomerCreditTransactionRequest;
import com.pos.customers.repository.CustomerRepository;
import com.pos.customers.service.CustomerCreditService;
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
import com.pos.sales.dto.SaleReceiptResponse;
import com.pos.sales.dto.SaleResponse;
import com.pos.sales.dto.SaleResumeRequest;
import com.pos.sales.dto.SaleSummaryResponse;
import com.pos.sales.repository.CashTransactionRepository;
import com.pos.sales.repository.IdempotencyKeyRepository;
import com.pos.sales.repository.PaymentMethodRepository;
import com.pos.sales.repository.SaleRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.HexFormat;
import java.util.List;
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
    private final CustomerCreditService customerCreditService;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;
    private final com.pos.promotions.repository.PromotionRepository promotionRepository;

    public SaleService(
            SaleRepository saleRepository,
            RegisterSessionRepository sessionRepository,
            PaymentMethodRepository paymentMethodRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            CashTransactionRepository cashTransactionRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            InventoryService inventoryService,
            CustomerCreditService customerCreditService,
            StoreScopeEvaluator storeScopeEvaluator,
            UserRepository userRepository,
            AuditRecorder auditRecorder,
            com.pos.promotions.repository.PromotionRepository promotionRepository) {
        this.saleRepository = saleRepository;
        this.sessionRepository = sessionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.inventoryService = inventoryService;
        this.customerCreditService = customerCreditService;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
        this.promotionRepository = promotionRepository;
    }

    @Transactional(readOnly = true)
    public Page<SaleSummaryResponse> search(
            String query,
            String status,
            UUID customerId,
            UUID cashierId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable) {
        var storeIds = storeScopeEvaluator.permittedStoreIds();
        if (storeIds.isEmpty()) {
            return Page.empty(pageable);
        }
        OffsetDateTime fromBound = from == null ? OffsetDateTime.parse("1970-01-01T00:00:00Z") : from;
        OffsetDateTime toBound = to == null ? OffsetDateTime.parse("9999-12-31T23:59:59Z") : to;
        return saleRepository.search(
                        storeIds,
                        query == null ? "" : query.trim(),
                        status == null ? "" : status.trim(),
                        customerId,
                        cashierId,
                        fromBound,
                        toBound,
                        pageable)
                .map(SaleSummaryResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<SaleSummaryResponse> listForCustomer(UUID customerId, Pageable pageable) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Customer not found"));
        return search(null, null, customerId, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public SaleReceiptResponse receipt(UUID id) {
        return SaleReceiptResponse.fromEntity(requireAccessible(id));
    }

    @Transactional
    public SaleReceiptResponse reprint(UUID id) {
        Sale sale = requireAccessible(id);
        User actor = currentUser();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(actor.getId()),
                "RECEIPT_REPRINTED",
                "Sale",
                sale.getId()));
        return SaleReceiptResponse.fromEntity(sale);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        return SaleResponse.fromEntity(requireAccessible(id));
    }

    private Sale requireAccessible(UUID id) {
        Sale sale = saleRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Sale not found"));
        if (!storeScopeEvaluator.canAccess(sale.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return sale;
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

        Sale sale = new Sale();
        sale.setStore(store);
        sale.setTerminal(register.getTerminal());
        sale.setRegister(register);
        sale.setRegisterSession(session);
        sale.setCashier(cashier);
        sale.setCustomer(customer);
        boolean completing = request.payments() != null && !request.payments().isEmpty();
        sale.setStatus(completing ? Sale.STATUS_COMPLETED : Sale.STATUS_HELD);
        sale.setDiscountTotal(ZERO);
        sale.setCurrencyCode(store.getCurrencyCode());
        sale.setReceiptNumber(nextReceiptNumber());

        boolean hasManualDiscountPermission = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SALE_DISCOUNT"));

        List<com.pos.promotions.domain.Promotion> activePromotions = promotionRepository.findActiveByStore(store.getId(), OffsetDateTime.now());

        BigDecimal subtotal = ZERO;
        BigDecimal taxTotal = ZERO;
        BigDecimal totalDiscount = ZERO;
        for (SaleItemRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
            if (!product.isActive()) {
                throw new ApiException(ErrorCode.RESOURCE_INACTIVE, "Product is inactive");
            }
            BigDecimal unitPrice = money(product.getSellingPrice());
            BigDecimal lineSubtotal = money(unitPrice.multiply(line.quantity()));

            BigDecimal lineDiscount = ZERO;
            
            if (line.discountAmount() != null && line.discountAmount().compareTo(ZERO) > 0) {
                if (!hasManualDiscountPermission) {
                    throw new ApiException(ErrorCode.ACCESS_DENIED, "Manual discounts require SALE_DISCOUNT permission");
                }
                lineDiscount = money(line.discountAmount());
            } else {
                for (com.pos.promotions.domain.Promotion p : activePromotions) {
                    BigDecimal promoDiscount = calculatePromotionDiscount(p, product, lineSubtotal);
                    if (promoDiscount.compareTo(ZERO) > 0) {
                        lineDiscount = lineDiscount.add(promoDiscount);
                        if (!p.isStackable()) {
                            break;
                        }
                    }
                }
            }
            
            if (lineDiscount.compareTo(lineSubtotal) > 0) {
                lineDiscount = lineSubtotal;
            }

            BigDecimal lineAfterDiscount = lineSubtotal.subtract(lineDiscount);
            BigDecimal taxAmount = money(lineAfterDiscount.multiply(product.getTaxRate()));
            BigDecimal lineTotal = money(lineAfterDiscount.add(taxAmount));

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setDiscountAmount(lineDiscount);
            item.setTaxAmount(taxAmount);
            item.setLineTotal(lineTotal);
            sale.addItem(item);

            subtotal = subtotal.add(lineSubtotal);
            totalDiscount = totalDiscount.add(lineDiscount);
            taxTotal = taxTotal.add(taxAmount);
        }

        BigDecimal grandTotal = money(subtotal.subtract(totalDiscount).add(taxTotal));

        sale.setSubtotal(subtotal);
        sale.setDiscountTotal(totalDiscount);
        sale.setTaxTotal(taxTotal);
        sale.setGrandTotal(grandTotal);
        if (completing) {
            applyPayments(sale, request.payments(), customer, grandTotal);
        }

        Sale saved = saleRepository.save(sale);

        if (completing) {
            completeSettlement(saved, store, session, cashier);
        }

        persistIdempotency(idempotencyKey.trim(), hash, saved, existing);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(cashier.getId()),
                completing ? "SALE_CREATED" : "SALE_HELD",
                "Sale",
                saved.getId()));

        return SaleResponse.fromEntity(saleRepository.findDetailedById(saved.getId()).orElse(saved));
    }

    @Transactional
    public SaleResponse hold(UUID id) {
        Sale sale = requireAccessible(id);
        if (Sale.STATUS_COMPLETED.equals(sale.getStatus())) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "A completed sale cannot be held");
        }
        return SaleResponse.fromEntity(sale);
    }

    @Transactional
    public SaleResponse resume(UUID id, SaleResumeRequest request) {
        Sale sale = requireAccessible(id);
        if (!Sale.STATUS_HELD.equals(sale.getStatus())) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only a held sale can be resumed");
        }

        RegisterSession session = sessionRepository.findByIdForUpdate(request.registerSessionId())
                .orElseThrow(() -> new ApiException(ErrorCode.REGISTER_SESSION_REQUIRED, "Register session not found"));
        if (!session.isOpen()) {
            throw new ApiException(ErrorCode.REGISTER_SESSION_REQUIRED, "Register session is not open");
        }
        Register register = session.getRegister();
        Store store = register.getStore();
        if (!store.getId().equals(sale.getStore().getId())) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Resume session must belong to the sale store");
        }

        User cashier = currentUser();
        sale.setRegisterSession(session);
        sale.setRegister(register);
        sale.setTerminal(register.getTerminal());
        sale.setCashier(cashier);
        sale.setStatus(Sale.STATUS_COMPLETED);
        applyPayments(sale, request.payments(), sale.getCustomer(), sale.getGrandTotal());

        Sale saved = saleRepository.save(sale);
        completeSettlement(saved, store, session, cashier);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(cashier.getId()),
                "SALE_RESUMED",
                "Sale",
                saved.getId()));

        return SaleResponse.fromEntity(saleRepository.findDetailedById(saved.getId()).orElse(saved));
    }

    private void completeSettlement(Sale saved, Store store, RegisterSession session, User cashier) {
        for (SaleItem item : saved.getItems()) {
            inventoryService.deductForSale(
                    store.getId(),
                    item.getProduct().getId(),
                    item.getQuantity(),
                    saved.getId());
        }
        settleNonInventoryEffects(saved, session, cashier, store);
    }

    private void applyPayments(Sale sale, List<SalePaymentRequest> payments, Customer customer, BigDecimal grandTotal) {
        if (payments.size() == 1) {
            PaymentMethod method = requireActiveMethod(payments.getFirst().paymentMethodId());
            if (PaymentMethod.CASH.equals(method.getCode())) {
                addPayment(sale, method, grandTotal);
                return;
            }
        }

        BigDecimal sum = ZERO;
        for (SalePaymentRequest line : payments) {
            PaymentMethod method = requireActiveMethod(line.paymentMethodId());
            if (PaymentMethod.STORE_CREDIT.equals(method.getCode()) && customer == null) {
                throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Store credit requires a customer");
            }
            BigDecimal amount = money(line.amount());
            addPayment(sale, method, amount);
            sum = sum.add(amount);
        }
        if (sum.compareTo(grandTotal) != 0) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Payment amounts must equal the sale total");
        }
    }

    private void settleNonInventoryEffects(Sale saved, RegisterSession session, User cashier, Store store) {
        BigDecimal cashTotal = ZERO;
        for (SalePayment payment : saved.getPayments()) {
            String code = payment.getPaymentMethod().getCode();
            if (PaymentMethod.CASH.equals(code)) {
                cashTotal = cashTotal.add(payment.getAmount());
            } else if (PaymentMethod.STORE_CREDIT.equals(code)) {
                customerCreditService.post(
                        saved.getCustomer().getId(),
                        new CustomerCreditTransactionRequest(
                                CreditTransactionType.REDEEM,
                                payment.getAmount(),
                                store.getCurrencyCode(),
                                "Sale",
                                saved.getId()));
            }
        }
        if (cashTotal.compareTo(ZERO) > 0) {
            CashTransaction cash = new CashTransaction();
            cash.setRegisterSession(session);
            cash.setTransactionType(CashTransaction.TYPE_SALE);
            cash.setAmount(cashTotal);
            cash.setReferenceType("Sale");
            cash.setReferenceId(saved.getId());
            cash.setCreatedBy(cashier);
            cashTransactionRepository.save(cash);
        }
    }

    private PaymentMethod requireActiveMethod(UUID paymentMethodId) {
        PaymentMethod method = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Payment method not found"));
        if (!method.isActive()) {
            throw new ApiException(ErrorCode.RESOURCE_INACTIVE, "Payment method is inactive");
        }
        return method;
    }

    private static void addPayment(Sale sale, PaymentMethod method, BigDecimal amount) {
        SalePayment payment = new SalePayment();
        payment.setPaymentMethod(method);
        payment.setAmount(amount);
        payment.setStatus(SalePayment.STATUS_COMPLETED);
        sale.addPayment(payment);
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

    private BigDecimal calculatePromotionDiscount(com.pos.promotions.domain.Promotion p, Product product, BigDecimal lineSubtotal) {
        boolean eligible = true;
        if (!p.getRules().isEmpty()) {
            eligible = false;
            for (com.pos.promotions.domain.PromotionRule rule : p.getRules()) {
                if (rule.getRuleType().equals(com.pos.promotions.domain.PromotionRule.RULE_MIN_AMOUNT)) {
                    if (lineSubtotal.compareTo(new BigDecimal(rule.getRuleValue())) >= 0) {
                        eligible = true;
                    }
                } else if (rule.getRuleType().equals(com.pos.promotions.domain.PromotionRule.RULE_SPECIFIC_PRODUCT)) {
                    if (product.getId().toString().equals(rule.getRuleValue())) {
                        eligible = true;
                    }
                } else if (rule.getRuleType().equals(com.pos.promotions.domain.PromotionRule.RULE_SPECIFIC_CATEGORY)) {
                    if (product.getCategory() != null && product.getCategory().getId().toString().equals(rule.getRuleValue())) {
                        eligible = true;
                    }
                }
            }
        }
        
        if (!eligible) return ZERO;

        if (p.getType().equals(com.pos.promotions.domain.Promotion.TYPE_PERCENTAGE)) {
            return money(lineSubtotal.multiply(p.getDiscountValue()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        } else if (p.getType().equals(com.pos.promotions.domain.Promotion.TYPE_FIXED_AMOUNT)) {
            return money(p.getDiscountValue());
        }
        return ZERO;
    }
}
