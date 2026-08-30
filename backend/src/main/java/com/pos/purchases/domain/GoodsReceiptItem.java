package com.pos.purchases.domain;

import com.pos.catalog.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    public GoodsReceiptItem() {
    }

    public GoodsReceiptItem(
            GoodsReceipt goodsReceipt,
            Product product,
            BigDecimal quantity,
            String batchNumber,
            LocalDate expirationDate,
            LocalDate manufacturingDate) {
        this.goodsReceipt = goodsReceipt;
        this.product = product;
        this.quantity = quantity;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.manufacturingDate = manufacturingDate;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public LocalDate getManufacturingDate() {
        return manufacturingDate;
    }
}
