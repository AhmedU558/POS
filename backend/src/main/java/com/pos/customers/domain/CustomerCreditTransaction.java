package com.pos.customers.domain;

import com.pos.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_credit_transactions")
public class CustomerCreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_credit_id", nullable = false)
    private CustomerCredit customerCredit;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private CreditTransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public CustomerCreditTransaction() {
    }

    public CustomerCreditTransaction(
            CustomerCredit customerCredit,
            CreditTransactionType transactionType,
            BigDecimal amount,
            String referenceType,
            UUID referenceId,
            BigDecimal balanceAfter,
            User createdBy) {
        this.customerCredit = customerCredit;
        this.transactionType = transactionType;
        this.amount = amount;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.balanceAfter = balanceAfter;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public CustomerCredit getCustomerCredit() {
        return customerCredit;
    }

    public CreditTransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
