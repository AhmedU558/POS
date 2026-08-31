package com.pos.finance.controller;

import com.pos.finance.dto.ExpenseRequest;
import com.pos.finance.dto.ExpenseResponse;
import com.pos.finance.service.ExpenseService;
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
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public Page<ExpenseResponse> list(Pageable pageable) {
        return expenseService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_WRITE')")
    public ResponseEntity<ExpenseResponse> create(@RequestBody @Valid ExpenseRequest request) {
        return ResponseEntity.ok(expenseService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public ResponseEntity<ExpenseResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(expenseService.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_WRITE')")
    public ResponseEntity<ExpenseResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid ExpenseRequest request) {
        return ResponseEntity.ok(expenseService.update(id, request));
    }
}
