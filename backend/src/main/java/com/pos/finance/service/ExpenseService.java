package com.pos.finance.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.finance.domain.Expense;
import com.pos.finance.dto.ExpenseRequest;
import com.pos.finance.dto.ExpenseResponse;
import com.pos.finance.repository.ExpenseRepository;
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
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final StoreRepository storeRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            StoreRepository storeRepository,
            StoreScopeEvaluator storeScopeEvaluator,
            UserRepository userRepository,
            AuditRecorder auditRecorder) {
        this.expenseRepository = expenseRepository;
        this.storeRepository = storeRepository;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> list(Pageable pageable) {
        var storeIds = storeScopeEvaluator.permittedStoreIds();
        if (storeIds.isEmpty()) return Page.empty(pageable);
        return expenseRepository.search(storeIds, pageable).map(ExpenseResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID id) {
        return ExpenseResponse.fromEntity(requireAccessible(id));
    }

    private Expense requireAccessible(UUID id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Expense not found"));
        if (!storeScopeEvaluator.canAccess(expense.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return expense;
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        if (!storeScopeEvaluator.canAccess(request.storeId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        Expense expense = new Expense();
        expense.setStore(store);
        expense.setCategory(request.category());
        expense.setAmount(request.amount());
        expense.setExpenseDate(request.expenseDate());
        expense.setDescription(request.description());
        expense.setCreatedBy(currentUser());

        Expense saved = expenseRepository.save(expense);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "EXPENSE_CREATED",
                "Expense",
                saved.getId()));

        return ExpenseResponse.fromEntity(saved);
    }

    @Transactional
    public ExpenseResponse update(UUID id, ExpenseRequest request) {
        Expense expense = requireAccessible(id);

        expense.setCategory(request.category());
        expense.setAmount(request.amount());
        expense.setExpenseDate(request.expenseDate());
        expense.setDescription(request.description());

        Expense saved = expenseRepository.save(expense);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "EXPENSE_UPDATED",
                "Expense",
                saved.getId()));

        return ExpenseResponse.fromEntity(saved);
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));
    }
}
