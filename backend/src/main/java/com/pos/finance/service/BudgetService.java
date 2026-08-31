package com.pos.finance.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.finance.domain.Budget;
import com.pos.finance.domain.BudgetLine;
import com.pos.finance.dto.BudgetLineRequest;
import com.pos.finance.dto.BudgetRequest;
import com.pos.finance.dto.BudgetResponse;
import com.pos.finance.repository.BudgetRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final StoreRepository storeRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    public BudgetService(
            BudgetRepository budgetRepository,
            StoreRepository storeRepository,
            StoreScopeEvaluator storeScopeEvaluator,
            UserRepository userRepository,
            AuditRecorder auditRecorder) {
        this.budgetRepository = budgetRepository;
        this.storeRepository = storeRepository;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<BudgetResponse> list(Pageable pageable) {
        var storeIds = storeScopeEvaluator.permittedStoreIds();
        if (storeIds.isEmpty()) return Page.empty(pageable);
        return budgetRepository.search(storeIds, pageable).map(BudgetResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(UUID id) {
        return BudgetResponse.fromEntity(requireAccessible(id));
    }

    private Budget requireAccessible(UUID id) {
        Budget budget = budgetRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Budget not found"));
        if (!storeScopeEvaluator.canAccess(budget.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return budget;
    }

    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        if (!storeScopeEvaluator.canAccess(request.storeId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        Budget budget = new Budget();
        budget.setStore(store);
        budget.setName(request.name());
        budget.setPeriodStart(request.periodStart());
        budget.setPeriodEnd(request.periodEnd());
        budget.setCreatedBy(currentUser());

        for (BudgetLineRequest lineReq : request.lines()) {
            BudgetLine line = new BudgetLine();
            line.setCategory(lineReq.category());
            line.setAllocatedAmount(lineReq.allocatedAmount());
            budget.addLine(line);
        }

        Budget saved = budgetRepository.save(budget);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "BUDGET_CREATED",
                "Budget",
                saved.getId()));

        return BudgetResponse.fromEntity(saved);
    }

    @Transactional
    public BudgetResponse update(UUID id, BudgetRequest request) {
        Budget budget = requireAccessible(id);

        if (!budget.getStatus().equals(Budget.STATUS_DRAFT)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only draft budgets can be modified");
        }

        budget.setName(request.name());
        budget.setPeriodStart(request.periodStart());
        budget.setPeriodEnd(request.periodEnd());

        budget.getLines().clear();
        for (BudgetLineRequest lineReq : request.lines()) {
            BudgetLine line = new BudgetLine();
            line.setCategory(lineReq.category());
            line.setAllocatedAmount(lineReq.allocatedAmount());
            budget.addLine(line);
        }

        Budget saved = budgetRepository.save(budget);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "BUDGET_UPDATED",
                "Budget",
                saved.getId()));

        return BudgetResponse.fromEntity(saved);
    }

    @Transactional
    public void approve(UUID id) {
        Budget budget = requireAccessible(id);
        if (!budget.getStatus().equals(Budget.STATUS_DRAFT)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only draft budgets can be approved");
        }
        budget.setStatus(Budget.STATUS_APPROVED);
        budgetRepository.save(budget);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "BUDGET_APPROVED",
                "Budget",
                budget.getId()));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));
    }
}
