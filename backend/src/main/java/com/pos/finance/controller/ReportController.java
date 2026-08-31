package com.pos.finance.controller;

import com.pos.finance.dto.BudgetVarianceReportResponse;

import com.pos.finance.dto.SalesReportResponse;
import com.pos.finance.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/budget-variance")
    @PreAuthorize("hasAuthority('REPORT_FINANCE')")
    public ResponseEntity<List<BudgetVarianceReportResponse>> getBudgetVariance(
            @RequestParam UUID storeId,
            @RequestParam UUID budgetId) {
        return ResponseEntity.ok(reportService.getBudgetVariance(storeId, budgetId));
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('REPORT_SALES')")
    public ResponseEntity<List<SalesReportResponse>> getSales(
            @RequestParam UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.getSales(storeId, startDate, endDate));
    }

    @GetMapping("/sales/by-product")
    @PreAuthorize("hasAuthority('REPORT_SALES')")
    public ResponseEntity<List<SalesReportResponse>> getSalesByProduct(
            @RequestParam UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/sales/by-category")
    @PreAuthorize("hasAuthority('REPORT_SALES')")
    public ResponseEntity<List<SalesReportResponse>> getSalesByCategory(
            @RequestParam UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/sales/by-cashier")
    @PreAuthorize("hasAuthority('REPORT_SALES')")
    public ResponseEntity<List<SalesReportResponse>> getSalesByCashier(
            @RequestParam UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(List.of());
    }



    @GetMapping("/payables")
    @PreAuthorize("hasAuthority('REPORT_FINANCE')")
    public ResponseEntity<List<Object>> getPayables(
            @RequestParam UUID storeId) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/cash-registers")
    @PreAuthorize("hasAuthority('REPORT_CASH')")
    public ResponseEntity<List<Object>> getCashRegisters(
            @RequestParam UUID storeId) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/profit-loss")
    @PreAuthorize("hasAuthority('REPORT_FINANCE')")
    public ResponseEntity<List<Object>> getProfitLoss(
            @RequestParam UUID storeId) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAuthority('REPORT_FINANCE')")
    public ResponseEntity<List<Object>> getCashFlow(
            @RequestParam UUID storeId) {
        return ResponseEntity.ok(List.of());
    }
}
