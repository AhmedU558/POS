package com.pos.quotations.service;

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
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.quotations.domain.Quotation;
import com.pos.quotations.domain.QuotationItem;
import com.pos.quotations.dto.QuotationRequest;
import com.pos.quotations.dto.QuotationResponse;
import com.pos.quotations.repository.QuotationRepository;
import com.pos.sales.dto.SaleItemRequest;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.UUID;

@Service
public class QuotationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final QuotationRepository quotationRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CustomerRepository customerRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    public QuotationService(
            QuotationRepository quotationRepository,
            ProductRepository productRepository,
            StoreRepository storeRepository,
            CustomerRepository customerRepository,
            StoreScopeEvaluator storeScopeEvaluator,
            UserRepository userRepository,
            AuditRecorder auditRecorder) {
        this.quotationRepository = quotationRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.customerRepository = customerRepository;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<QuotationResponse> list(Pageable pageable) {
        var storeIds = storeScopeEvaluator.permittedStoreIds();
        if (storeIds.isEmpty()) return Page.empty(pageable);
        return quotationRepository.search(storeIds, pageable).map(QuotationResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public QuotationResponse get(UUID id) {
        return QuotationResponse.fromEntity(requireAccessible(id));
    }

    private Quotation requireAccessible(UUID id) {
        Quotation quotation = quotationRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Quotation not found"));
        if (!storeScopeEvaluator.canAccess(quotation.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return quotation;
    }

    @Transactional
    public QuotationResponse create(QuotationRequest request) {
        if (!storeScopeEvaluator.canAccess(request.storeId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        User createdBy = currentUser();
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Customer not found"));
        }

        Quotation quotation = new Quotation();
        quotation.setStore(store);
        quotation.setCustomer(customer);
        quotation.setCreatedBy(createdBy);
        quotation.setCurrencyCode(store.getCurrencyCode());
        quotation.setQuotationNumber(nextQuotationNumber());
        quotation.setExpirationDate(request.expirationDate());
        quotation.setNotes(request.notes());

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

            QuotationItem item = new QuotationItem();
            item.setProduct(product);
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setDiscountAmount(ZERO);
            item.setTaxAmount(taxAmount);
            item.setLineTotal(lineTotal);
            quotation.addItem(item);

            subtotal = subtotal.add(lineSubtotal);
            taxTotal = taxTotal.add(taxAmount);
        }

        BigDecimal grandTotal = money(subtotal.add(taxTotal));
        quotation.setSubtotal(subtotal);
        quotation.setTaxTotal(taxTotal);
        quotation.setGrandTotal(grandTotal);

        Quotation saved = quotationRepository.save(quotation);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(createdBy.getId()),
                "QUOTATION_CREATED",
                "Quotation",
                saved.getId()));

        return QuotationResponse.fromEntity(saved);
    }

    @Transactional
    public QuotationResponse updateStatus(UUID id, String status) {
        Quotation quotation = requireAccessible(id);
        quotation.setStatus(status);
        quotationRepository.save(quotation);
        
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "QUOTATION_STATUS_UPDATED",
                "Quotation",
                quotation.getId()));
                
        return QuotationResponse.fromEntity(quotation);
    }

    private String nextQuotationNumber() {
        return "QT-" + Year.now() + "-" + String.format("%06d", quotationRepository.nextQuotationSequence());
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
