package com.pos.inventory.domain;

import com.pos.catalog.entity.Product;
import com.pos.organization.domain.Store;
import com.pos.users.domain.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "batch_id")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryTransaction() {}

    public InventoryTransaction(Product product, Store store, TransactionType type, BigDecimal quantity, String reason, User createdBy) {
        this.product = product;
        this.store = store;
        this.transactionType = type;
        this.quantity = quantity;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void assignBatch(UUID batchId) {
        this.batchId = batchId;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public Store getStore() { return store; }
    public UUID getBatchId() { return batchId; }
    public TransactionType getTransactionType() { return transactionType; }
    public BigDecimal getQuantity() { return quantity; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getReason() { return reason; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}