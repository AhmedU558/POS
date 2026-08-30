package com.pos.accounts.service;

import com.pos.accounts.domain.SupplierInvoice;
import com.pos.accounts.domain.SupplierInvoiceStatus;
import com.pos.accounts.dto.SupplierInvoiceCreateRequest;
import com.pos.accounts.dto.SupplierInvoiceResponse;
import com.pos.accounts.dto.SupplierInvoiceUpdateRequest;
import com.pos.accounts.repository.SupplierInvoiceRepository;
import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.suppliers.domain.Supplier;
import com.pos.suppliers.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SupplierInvoiceService {

    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierRepository supplierRepository;
    private final AuditRecorder auditRecorder;

    public SupplierInvoiceService(
            SupplierInvoiceRepository invoiceRepository,
            SupplierRepository supplierRepository,
            AuditRecorder auditRecorder) {
        this.invoiceRepository = invoiceRepository;
        this.supplierRepository = supplierRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<SupplierInvoiceResponse> search(String query, SupplierInvoiceStatus status, Pageable pageable) {
        return invoiceRepository.search(blankToNull(query), status, pageable)
                .map(SupplierInvoiceResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public SupplierInvoiceResponse get(UUID id) {
        return SupplierInvoiceResponse.fromEntity(requireDetailed(id));
    }

    @Transactional
    public SupplierInvoiceResponse create(SupplierInvoiceCreateRequest request) {
        if (invoiceRepository.existsByInvoiceNumber(request.invoiceNumber().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Invoice number already exists");
        }
        SupplierInvoice invoice = new SupplierInvoice();
        invoice.setInvoiceNumber(request.invoiceNumber().trim());
        invoice.setSupplier(requireSupplier(request.supplierId()));
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        invoice.setTotalAmount(request.totalAmount());
        invoice.setStatus(SupplierInvoiceStatus.OPEN);
        invoice.setNotes(blankToNull(request.notes()));
        SupplierInvoice saved = invoiceRepository.save(invoice);
        audit("SUPPLIER_INVOICE_CREATED", saved.getId());
        return SupplierInvoiceResponse.fromEntity(requireDetailed(saved.getId()));
    }

    @Transactional
    public SupplierInvoiceResponse update(UUID id, SupplierInvoiceUpdateRequest request) {
        SupplierInvoice invoice = requireDetailed(id);
        if (invoice.getStatus() != SupplierInvoiceStatus.OPEN) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only open invoices can be updated");
        }
        if (invoiceRepository.existsByInvoiceNumberAndIdNot(request.invoiceNumber().trim(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Invoice number already exists");
        }
        invoice.setInvoiceNumber(request.invoiceNumber().trim());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        invoice.setTotalAmount(request.totalAmount());
        invoice.setNotes(blankToNull(request.notes()));
        invoiceRepository.save(invoice);
        audit("SUPPLIER_INVOICE_UPDATED", invoice.getId());
        return SupplierInvoiceResponse.fromEntity(invoice);
    }

    private SupplierInvoice requireDetailed(UUID id) {
        return invoiceRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier invoice not found"));
    }

    private Supplier requireSupplier(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier not found"));
    }

    private void audit(String action, UUID id) {
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                action,
                "SupplierInvoice",
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
