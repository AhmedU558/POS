package com.pos.sales.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.customers.domain.CreditTransactionType;
import com.pos.customers.domain.Customer;
import com.pos.customers.dto.CustomerCreditTransactionRequest;
import com.pos.customers.service.CustomerCreditService;
import com.pos.inventory.service.InventoryService;
import com.pos.organization.domain.Register;
import com.pos.organization.domain.RegisterSession;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.RegisterSessionRepository;
import com.pos.sales.domain.CashTransaction;
import com.pos.sales.domain.PaymentMethod;
import com.pos.sales.domain.RefundPayment;
import com.pos.sales.domain.Sale;
import com.pos.sales.domain.SaleItem;
import com.pos.sales.domain.SaleReturn;
import com.pos.sales.domain.SaleReturnItem;
import com.pos.sales.dto.SalePaymentRequest;
import com.pos.sales.dto.SaleReturnItemRequest;
import com.pos.sales.dto.SaleReturnRequest;
import com.pos.sales.dto.SaleReturnResponse;
import com.pos.sales.repository.CashTransactionRepository;
import com.pos.sales.repository.PaymentMethodRepository;
import com.pos.sales.repository.SaleRepository;
import com.pos.sales.repository.SaleReturnRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
public class SaleReturnService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final RegisterSessionRepository sessionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final InventoryService inventoryService;
    private final CustomerCreditService customerCreditService;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    public SaleReturnService(
            SaleRepository saleRepository,
            SaleReturnRepository saleReturnRepository,
            RegisterSessionRepository sessionRepository,
            PaymentMethodRepository paymentMethodRepository,
            CashTransactionRepository cashTransactionRepository,
            InventoryService inventoryService,
            CustomerCreditService customerCreditService,
            StoreScopeEvaluator storeScopeEvaluator,
            UserRepository userRepository,
            AuditRecorder auditRecorder) {
        this.saleRepository = saleRepository;
        this.saleReturnRepository = saleReturnRepository;
        this.sessionRepository = sessionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.inventoryService = inventoryService;
        this.customerCreditService = customerCreditService;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public SaleReturnResponse processReturn(UUID saleId, SaleReturnRequest request) {
        Sale sale = saleRepository.findDetailedById(saleId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Sale not found"));

        if (!storeScopeEvaluator.canAccess(sale.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        if (!sale.getStatus().equals(Sale.STATUS_COMPLETED)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only completed sales can be returned");
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
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Return context does not match the open register session");
        }

        User cashier = currentUser();

        SaleReturn saleReturn = new SaleReturn();
        saleReturn.setSale(sale);
        saleReturn.setStore(store);
        saleReturn.setTerminal(register.getTerminal());
        saleReturn.setRegister(register);
        saleReturn.setRegisterSession(session);
        saleReturn.setCashier(cashier);
        saleReturn.setCustomer(sale.getCustomer());
        saleReturn.setStatus(SaleReturn.STATUS_COMPLETED);
        saleReturn.setCurrencyCode(store.getCurrencyCode());
        saleReturn.setReceiptNumber(nextReturnReceiptNumber());
        saleReturn.setReason(request.reason());

        BigDecimal subtotal = ZERO;
        BigDecimal taxTotal = ZERO;
        BigDecimal discountTotal = ZERO;

        for (SaleReturnItemRequest returnLine : request.items()) {
            SaleItem originalItem = sale.getItems().stream()
                    .filter(i -> i.getId().equals(returnLine.saleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Item not found in the original sale"));
            
            BigDecimal maxQuantity = originalItem.getQuantity(); // We should ideally subtract previously returned quantities if we support partial returns multiple times
            if (returnLine.returnQuantity().compareTo(maxQuantity) > 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Return quantity exceeds original sold quantity");
            }

            BigDecimal ratio = returnLine.returnQuantity().divide(originalItem.getQuantity(), 4, RoundingMode.HALF_UP);
            BigDecimal itemSubtotal = money(originalItem.getUnitPrice().multiply(returnLine.returnQuantity()));
            BigDecimal itemDiscount = money(originalItem.getDiscountAmount().multiply(ratio));
            BigDecimal itemTax = money(originalItem.getTaxAmount().multiply(ratio));
            BigDecimal itemTotal = money(itemSubtotal.subtract(itemDiscount).add(itemTax));

            SaleReturnItem item = new SaleReturnItem();
            item.setSaleItem(originalItem);
            item.setProduct(originalItem.getProduct());
            item.setQuantity(returnLine.returnQuantity());
            item.setUnitPrice(originalItem.getUnitPrice());
            item.setDiscountAmount(itemDiscount);
            item.setTaxAmount(itemTax);
            item.setLineTotal(itemTotal);
            item.setBatchId(originalItem.getBatchId());
            saleReturn.addItem(item);

            subtotal = subtotal.add(itemSubtotal);
            discountTotal = discountTotal.add(itemDiscount);
            taxTotal = taxTotal.add(itemTax);
        }

        BigDecimal grandTotal = money(subtotal.subtract(discountTotal).add(taxTotal));
        saleReturn.setSubtotal(subtotal);
        saleReturn.setDiscountTotal(discountTotal);
        saleReturn.setTaxTotal(taxTotal);
        saleReturn.setGrandTotal(grandTotal);

        applyRefundPayments(saleReturn, request.refundPayments(), sale.getCustomer(), grandTotal);

        SaleReturn saved = saleReturnRepository.save(saleReturn);
        completeReturnSettlement(saved, store, session, cashier);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(cashier.getId()),
                "SALE_REFUND",
                "SaleReturn",
                saved.getId()));

        return SaleReturnResponse.fromEntity(saved);
    }

    private void completeReturnSettlement(SaleReturn saved, Store store, RegisterSession session, User cashier) {
        for (SaleReturnItem item : saved.getItems()) {
            inventoryService.addForReturn(
                    store.getId(),
                    item.getProduct().getId(),
                    item.getQuantity(),
                    saved.getId());
        }
        
        BigDecimal cashTotal = ZERO;
        for (RefundPayment payment : saved.getPayments()) {
            String code = payment.getPaymentMethod().getCode();
            if (PaymentMethod.CASH.equals(code)) {
                cashTotal = cashTotal.add(payment.getAmount());
            } else if (PaymentMethod.STORE_CREDIT.equals(code)) {
                customerCreditService.post(
                        saved.getCustomer().getId(),
                        new CustomerCreditTransactionRequest(
                                CreditTransactionType.ISSUE,
                                payment.getAmount(),
                                store.getCurrencyCode(),
                                "Refund",
                                saved.getId()));
            }
        }
        if (cashTotal.compareTo(ZERO) > 0) {
            CashTransaction cash = new CashTransaction();
            cash.setRegisterSession(session);
            cash.setTransactionType(CashTransaction.TYPE_REFUND);
            cash.setAmount(cashTotal); // Note: the amount is positive here but semantically it's cash out. We may need to treat TYPE_REFUND as cash out in summaries.
            cash.setReferenceType("SaleReturn");
            cash.setReferenceId(saved.getId());
            cash.setCreatedBy(cashier);
            cashTransactionRepository.save(cash);
        }
    }

    private void applyRefundPayments(SaleReturn returnRecord, List<SalePaymentRequest> payments, Customer customer, BigDecimal grandTotal) {
        BigDecimal sum = ZERO;
        for (SalePaymentRequest line : payments) {
            PaymentMethod method = paymentMethodRepository.findById(line.paymentMethodId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Payment method not found"));
            if (!method.isActive()) {
                throw new ApiException(ErrorCode.RESOURCE_INACTIVE, "Payment method is inactive");
            }
            if (PaymentMethod.STORE_CREDIT.equals(method.getCode()) && customer == null) {
                throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Store credit requires a customer");
            }
            BigDecimal amount = money(line.amount());
            
            RefundPayment payment = new RefundPayment();
            payment.setPaymentMethod(method);
            payment.setAmount(amount);
            payment.setStatus("COMPLETED");
            returnRecord.addPayment(payment);
            sum = sum.add(amount);
        }
        if (sum.compareTo(grandTotal) != 0) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Refund payment amounts must equal the return total");
        }
    }

    private String nextReturnReceiptNumber() {
        return "RET-" + Year.now() + "-" + String.format("%06d", saleRepository.nextReceiptSequence());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));
    }
}
