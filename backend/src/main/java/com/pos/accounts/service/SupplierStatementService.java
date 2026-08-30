package com.pos.accounts.service;

import com.pos.accounts.domain.SupplierInvoice;
import com.pos.accounts.domain.SupplierInvoiceStatus;
import com.pos.accounts.domain.SupplierPayment;
import com.pos.accounts.domain.SupplierStatementLineType;
import com.pos.accounts.dto.SupplierStatementLineResponse;
import com.pos.accounts.repository.SupplierInvoiceRepository;
import com.pos.accounts.repository.SupplierPaymentRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.suppliers.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierStatementService {

    private final SupplierRepository supplierRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierPaymentRepository paymentRepository;

    public SupplierStatementService(
            SupplierRepository supplierRepository,
            SupplierInvoiceRepository invoiceRepository,
            SupplierPaymentRepository paymentRepository) {
        this.supplierRepository = supplierRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public Page<SupplierStatementLineResponse> statement(UUID supplierId, Pageable pageable) {
        if (!supplierRepository.existsById(supplierId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier not found");
        }

        List<StatementEvent> events = new ArrayList<>();
        for (SupplierInvoice invoice : invoiceRepository.findBySupplier_IdOrderByInvoiceDateAscCreatedAtAsc(supplierId)) {
            if (invoice.getStatus() == SupplierInvoiceStatus.CANCELLED) {
                continue;
            }
            events.add(new StatementEvent(
                    SupplierStatementLineType.INVOICE,
                    invoice.getInvoiceDate(),
                    invoice.getCreatedAt(),
                    invoice.getId(),
                    invoice.getInvoiceNumber(),
                    null,
                    invoice.getTotalAmount(),
                    BigDecimal.ZERO));
        }
        for (SupplierPayment payment : paymentRepository.findBySupplierId(supplierId)) {
            events.add(new StatementEvent(
                    SupplierStatementLineType.PAYMENT,
                    payment.getPaymentDate(),
                    payment.getCreatedAt(),
                    payment.getInvoice().getId(),
                    payment.getInvoice().getInvoiceNumber(),
                    payment.getId(),
                    BigDecimal.ZERO,
                    payment.getAmount()));
        }
        events.sort(Comparator
                .comparing(StatementEvent::date)
                .thenComparing(StatementEvent::type)
                .thenComparing(StatementEvent::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));

        List<SupplierStatementLineResponse> lines = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (StatementEvent event : events) {
            running = running.add(event.debit()).subtract(event.credit());
            lines.add(new SupplierStatementLineResponse(
                    event.type(),
                    event.date(),
                    event.invoiceId(),
                    event.invoiceNumber(),
                    event.paymentId(),
                    event.debit(),
                    event.credit(),
                    running));
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), lines.size());
        List<SupplierStatementLineResponse> page = start >= lines.size() ? List.of() : lines.subList(start, end);
        return new PageImpl<>(page, pageable, lines.size());
    }

    private record StatementEvent(
            SupplierStatementLineType type,
            LocalDate date,
            OffsetDateTime createdAt,
            UUID invoiceId,
            String invoiceNumber,
            UUID paymentId,
            BigDecimal debit,
            BigDecimal credit
    ) {
    }
}
