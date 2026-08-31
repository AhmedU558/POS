package com.pos.finance.service;

import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.finance.domain.Budget;
import com.pos.finance.domain.BudgetLine;
import com.pos.finance.domain.Expense;
import com.pos.finance.dto.BudgetVarianceReportResponse;
import com.pos.finance.dto.InventoryReportResponse;
import com.pos.finance.dto.SalesReportResponse;
import com.pos.finance.repository.BudgetRepository;
import com.pos.finance.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;

    public ReportService(
            BudgetRepository budgetRepository,
            ExpenseRepository expenseRepository,
            StoreScopeEvaluator storeScopeEvaluator) {
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.storeScopeEvaluator = storeScopeEvaluator;
    }

    @Transactional(readOnly = true)
    public List<BudgetVarianceReportResponse> getBudgetVariance(UUID storeId, UUID budgetId) {
        if (!storeScopeEvaluator.canAccess(storeId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        Budget budget = budgetRepository.findDetailedById(budgetId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Budget not found"));

        if (!budget.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Budget does not belong to store");
        }

        List<Expense> expenses = expenseRepository.findByStoreAndDateRange(
                storeId, budget.getPeriodStart(), budget.getPeriodEnd());

        Map<String, BigDecimal> actualsByCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        List<BudgetVarianceReportResponse> report = new ArrayList<>();
        for (BudgetLine line : budget.getLines()) {
            BigDecimal actual = actualsByCategory.getOrDefault(line.getCategory(), BigDecimal.ZERO);
            BigDecimal variance = line.getAllocatedAmount().subtract(actual);
            report.add(new BudgetVarianceReportResponse(
                    budget.getId(),
                    budget.getName(),
                    budget.getPeriodStart(),
                    budget.getPeriodEnd(),
                    line.getCategory(),
                    line.getAllocatedAmount(),
                    actual,
                    variance
            ));
        }

        return report;
    }

    @Transactional(readOnly = true)
    public List<SalesReportResponse> getSales(UUID storeId, LocalDate startDate, LocalDate endDate) {
        if (!storeScopeEvaluator.canAccess(storeId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        // Minimal implementation
        return List.of();
    }

    @Transactional(readOnly = true)
    public List<InventoryReportResponse> getInventory(UUID storeId) {
        if (!storeScopeEvaluator.canAccess(storeId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        // Minimal implementation
        return List.of();
    }
}
