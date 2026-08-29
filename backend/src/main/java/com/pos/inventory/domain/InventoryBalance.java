package com.pos.inventory.domain;

import com.pos.catalog.entity.Product;
import com.pos.organization.domain.Store;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_balances")
public class InventoryBalance {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    protected InventoryBalance() {
    }

    public InventoryBalance(Product product, Store store) {
        this.product = product;
        this.store = store;
        this.quantity = BigDecimal.ZERO;
        this.lastUpdatedAt = Instant.now();
    }

    public void addQuantity(BigDecimal amount) {
        this.quantity = this.quantity.add(amount);
        this.lastUpdatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public Store getStore() { return store; }
    public BigDecimal getQuantity() { return quantity; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
}