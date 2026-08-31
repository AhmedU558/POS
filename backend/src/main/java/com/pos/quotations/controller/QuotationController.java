package com.pos.quotations.controller;

import com.pos.quotations.domain.Quotation;
import com.pos.quotations.dto.QuotationRequest;
import com.pos.quotations.dto.QuotationResponse;
import com.pos.quotations.service.QuotationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private final QuotationService quotationService;

    public QuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('QUOTATION_READ')")
    public Page<QuotationResponse> list(Pageable pageable) {
        return quotationService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('QUOTATION_WRITE')")
    public ResponseEntity<QuotationResponse> create(@RequestBody @Valid QuotationRequest request) {
        return ResponseEntity.ok(quotationService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTATION_READ')")
    public ResponseEntity<QuotationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(quotationService.get(id));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('QUOTATION_SEND')")
    public ResponseEntity<QuotationResponse> send(@PathVariable UUID id) {
        return ResponseEntity.ok(quotationService.updateStatus(id, Quotation.STATUS_SENT));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('QUOTATION_APPROVE')")
    public ResponseEntity<QuotationResponse> accept(@PathVariable UUID id) {
        return ResponseEntity.ok(quotationService.updateStatus(id, Quotation.STATUS_ACCEPTED));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('QUOTATION_APPROVE')")
    public ResponseEntity<QuotationResponse> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(quotationService.updateStatus(id, Quotation.STATUS_REJECTED));
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('QUOTATION_CONVERT')")
    public ResponseEntity<QuotationResponse> convert(@PathVariable UUID id) {
        return ResponseEntity.ok(quotationService.updateStatus(id, Quotation.STATUS_CONVERTED));
    }
}
