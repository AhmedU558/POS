package com.pos.inventory.domain;

import com.pos.catalog.entity.Product;
import com.pos.organization.domain.Store;
import com.pos.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stock_alerts")
public class StockAlert {

    public static final String TYPE_LOW_STOCK = "LOW_STOCK";
    public static final String TYPE_EXPIRY = "EXPIRY";
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    @Column(name = "alert_type", nullable = false, length = 40)
    private String alertType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "minimum_level", precision = 19, scale = 4)
    private BigDecimal minimumLevel;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockAlert() {
    }

    public static StockAlert lowStock(Store store, Product product, BigDecimal quantity, BigDecimal minimumLevel) {
        StockAlert alert = new StockAlert();
        alert.store = store;
        alert.product = product;
        alert.alertType = TYPE_LOW_STOCK;
        alert.quantity = quantity;
        alert.minimumLevel = minimumLevel;
        alert.status = STATUS_OPEN;
        alert.createdAt = Instant.now();
        alert.updatedAt = alert.createdAt;
        return alert;
    }

    public static StockAlert expiry(Store store, Product product, InventoryBatch batch, BigDecimal quantity, LocalDate expirationDate) {
        StockAlert alert = new StockAlert();
        alert.store = store;
        alert.product = product;
        alert.batch = batch;
        alert.alertType = TYPE_EXPIRY;
        alert.quantity = quantity;
        alert.expirationDate = expirationDate;
        alert.status = STATUS_OPEN;
        alert.createdAt = Instant.now();
        alert.updatedAt = alert.createdAt;
        return alert;
    }

    public void refresh(BigDecimal quantity, BigDecimal minimumLevel, LocalDate expirationDate) {
        this.quantity = quantity;
        this.minimumLevel = minimumLevel;
        this.expirationDate = expirationDate;
        this.updatedAt = Instant.now();
    }

    public void acknowledge(User user) {
        this.status = STATUS_ACKNOWLEDGED;
        this.acknowledgedAt = Instant.now();
        this.acknowledgedBy = user;
        this.updatedAt = this.acknowledgedAt;
    }

    public UUID getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public Product getProduct() {
        return product;
    }

    public InventoryBatch getBatch() {
        return batch;
    }

    public String getAlertType() {
        return alertType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getMinimumLevel() {
        return minimumLevel;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public String getStatus() {
        return status;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public User getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
