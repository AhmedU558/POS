package com.pos.finance.controller;

import com.pos.finance.dto.BudgetRequest;
import com.pos.finance.dto.BudgetResponse;
import com.pos.finance.service.BudgetService;
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
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BUDGET_READ')")
    public Page<BudgetResponse> list(Pageable pageable) {
        return budgetService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BUDGET_WRITE')")
    public ResponseEntity<BudgetResponse> create(@RequestBody @Valid BudgetRequest request) {
        return ResponseEntity.ok(budgetService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BUDGET_READ')")
    public ResponseEntity<BudgetResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(budgetService.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('BUDGET_WRITE')")
    public ResponseEntity<BudgetResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid BudgetRequest request) {
        return ResponseEntity.ok(budgetService.update(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('BUDGET_APPROVE')")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        budgetService.approve(id);
        return ResponseEntity.noContent().build();
    }
}
