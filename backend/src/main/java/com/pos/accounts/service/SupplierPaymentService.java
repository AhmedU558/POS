package com.pos.accounts.service;

import com.pos.accounts.domain.SupplierInvoice;
import com.pos.accounts.domain.SupplierInvoiceStatus;
import com.pos.accounts.domain.SupplierPayment;
import com.pos.accounts.dto.PayablesSummaryResponse;
import com.pos.accounts.dto.SupplierInvoiceResponse;
import com.pos.accounts.dto.SupplierPaymentCreateRequest;
import com.pos.accounts.dto.SupplierPaymentResponse;
import com.pos.accounts.repository.SupplierInvoiceRepository;
import com.pos.accounts.repository.SupplierPaymentRepository;
import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class SupplierPaymentService {

    private final SupplierPaymentRepository paymentRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final AuditRecorder auditRecorder;

    public SupplierPaymentService(
            SupplierPaymentRepository paymentRepository,
            SupplierInvoiceRepository invoiceRepository,
            AuditRecorder auditRecorder) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<SupplierPaymentResponse> search(UUID invoiceId, Pageable pageable) {
        Page<SupplierPayment> page = invoiceId == null
                ? paymentRepository.findAllBy(pageable)
                : paymentRepository.findByInvoice_Id(invoiceId, pageable);
        return page.map(SupplierPaymentResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<SupplierInvoiceResponse> overdue(Pageable pageable) {
        return invoiceRepository.findOverdue(LocalDate.now(), pageable)
                .map(SupplierInvoiceResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public PayablesSummaryResponse summary() {
        LocalDate today = LocalDate.now();
        return new PayablesSummaryResponse(
                invoiceRepository.sumTotalInvoiced(),
                invoiceRepository.sumPaid(),
                invoiceRepository.sumOutstanding(),
                invoiceRepository.sumOverdue(today));
    }

    @Transactional
    public SupplierPaymentResponse create(SupplierPaymentCreateRequest request) {
        SupplierInvoice invoice = invoiceRepository.findByIdForUpdate(request.invoiceId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier invoice not found"));
        if (invoice.getStatus() != SupplierInvoiceStatus.OPEN) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Payments are allowed only on open invoices");
        }
        if (request.amount().compareTo(invoice.remainingAmount()) > 0) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Payment cannot exceed the remaining amount");
        }

        invoice.applyPayment(request.amount());
        invoiceRepository.save(invoice);

        SupplierPayment payment = new SupplierPayment();
        payment.setInvoice(invoice);
        payment.setAmount(request.amount());
        payment.setPaymentDate(request.paymentDate());
        payment.setPaymentMethod(request.method());
        payment.setReference(blankToNull(request.reference()));
        SupplierPayment saved = paymentRepository.save(payment);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                "SUPPLIER_PAYMENT_CREATED",
                "SupplierPayment",
                saved.getId()));

        return SupplierPaymentResponse.fromEntity(saved);
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
