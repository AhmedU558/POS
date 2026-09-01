package com.pos.customers.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.customers.domain.CreditTransactionType;
import com.pos.customers.domain.Customer;
import com.pos.customers.domain.CustomerCredit;
import com.pos.customers.domain.CustomerCreditTransaction;
import com.pos.customers.dto.CustomerCreditResponse;
import com.pos.customers.dto.CustomerCreditTransactionRequest;
import com.pos.customers.dto.CustomerCreditTransactionResponse;
import com.pos.customers.repository.CustomerCreditRepository;
import com.pos.customers.repository.CustomerCreditTransactionRepository;
import com.pos.customers.repository.CustomerRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CustomerCreditService {

    private final CustomerRepository customerRepository;
    private final CustomerCreditRepository creditRepository;
    private final CustomerCreditTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    public CustomerCreditService(
            CustomerRepository customerRepository,
            CustomerCreditRepository creditRepository,
            CustomerCreditTransactionRepository transactionRepository,
            UserRepository userRepository,
            AuditRecorder auditRecorder) {
        this.customerRepository = customerRepository;
        this.creditRepository = creditRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public CustomerCreditResponse get(UUID customerId, Pageable pageable) {
        Customer customer = requireCustomer(customerId);
        CustomerCredit credit = creditRepository.findByCustomerId(customerId).orElse(null);
        return toResponse(customer, credit, pageable);
    }

    @Transactional
    public CustomerCreditResponse post(UUID customerId, CustomerCreditTransactionRequest request) {
        Customer customer = requireCustomer(customerId);
        if (!customer.isActive()) {
            throw new ApiException(ErrorCode.RESOURCE_INACTIVE, "Customer is inactive");
        }

        BigDecimal postedAmount = postedAmount(request);
        String currencyCode = normalizeCurrency(request.currencyCode());

        CustomerCredit credit = lockOrCreateAccount(customer, currencyCode);
        assertCurrencyMatches(credit, currencyCode);

        BigDecimal newBalance = credit.getBalance().add(postedAmount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Store credit balance cannot be negative");
        }
        if (customer.getCreditLimit() != null && customer.getCreditLimit().compareTo(BigDecimal.ZERO) > 0 && newBalance.compareTo(customer.getCreditLimit()) > 0) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Transaction exceeds customer credit limit");
        }

        credit.setBalance(newBalance);
        creditRepository.save(credit);

        User actor = currentUser();
        CustomerCreditTransaction transaction = new CustomerCreditTransaction(
                credit,
                request.transactionType(),
                postedAmount,
                blankToNull(request.referenceType()),
                request.referenceId(),
                newBalance,
                actor);
        transactionRepository.save(transaction);

        auditRecorder.record(AuditEvent.of(
                actor != null ? AuditActor.user(actor.getId()) : AuditActor.system(),
                auditAction(request.transactionType()),
                "CustomerCreditTransaction",
                transaction.getId()));

        return toResponse(customer, credit, Pageable.ofSize(50));
    }

    private CustomerCredit lockOrCreateAccount(Customer customer, String currencyCode) {
        if (creditRepository.findByCustomerId(customer.getId()).isEmpty()) {
            if (currencyCode == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "currencyCode is required for the first credit transaction");
            }
            creditRepository.insertZeroAccountIfAbsent(customer.getId(), currencyCode);
        }
        return creditRepository.findByCustomerIdForUpdate(customer.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Credit account could not be created"));
    }

    private static void assertCurrencyMatches(CustomerCredit credit, String currencyCode) {
        if (currencyCode == null) {
            return;
        }
        if (!currencyCode.equalsIgnoreCase(credit.getCurrencyCode().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Currency does not match the credit account");
        }
    }

    private static BigDecimal postedAmount(CustomerCreditTransactionRequest request) {
        BigDecimal amount = request.amount();
        if (request.transactionType() == CreditTransactionType.ADJUST) {
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "ADJUST amount cannot be zero");
            }
            return amount;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "amount must be greater than zero");
        }
        if (request.transactionType() == CreditTransactionType.REDEEM) {
            return amount.negate();
        }
        return amount;
    }

    private CustomerCreditResponse toResponse(Customer customer, CustomerCredit credit, Pageable pageable) {
        Page<CustomerCreditTransactionResponse> transactions = credit == null
                ? Page.empty(pageable)
                : transactionRepository
                        .findByCustomerCreditIdOrderByCreatedAtDesc(credit.getId(), pageable)
                        .map(CustomerCreditTransactionResponse::fromEntity);
        return CustomerCreditResponse.of(customer, credit, transactions);
    }

    private Customer requireCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Customer not found"));
    }

    private static String auditAction(CreditTransactionType type) {
        return switch (type) {
            case ISSUE -> "CREDIT_ISSUED";
            case REDEEM -> "CREDIT_REDEEMED";
            case ADJUST -> "CREDIT_ADJUSTED";
        };
    }

    private static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return null;
        }
        String trimmed = currencyCode.trim().toUpperCase();
        if (trimmed.length() != 3 || !trimmed.chars().allMatch(Character::isLetter)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "currencyCode must be a 3-letter ISO-4217 code");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private User currentUser() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return null;
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.equals("anonymousUser")) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
    }
}
