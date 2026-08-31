package com.pos.sales.domain;

import com.pos.customers.domain.Customer;
import com.pos.organization.domain.Register;
import com.pos.organization.domain.RegisterSession;
import com.pos.organization.domain.Store;
import com.pos.organization.domain.Terminal;
import com.pos.users.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sales")
public class Sale {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_HELD = "HELD";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false)
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_session_id", nullable = false)
    private RegisterSession registerSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 30)
    private String status = STATUS_COMPLETED;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "fbr_provider_code", length = 50)
    private String fbrProviderCode;

    @Column(name = "fbr_environment", length = 20)
    private String fbrEnvironment;

    @Column(name = "fbr_status", length = 30)
    private String fbrStatus = "NOT_CONFIGURED";

    @Column(name = "fbr_request_id", length = 100)
    private String fbrRequestId;

    @Column(name = "fbr_submitted_at")
    private OffsetDateTime fbrSubmittedAt;

    @Column(name = "fbr_completed_at")
    private OffsetDateTime fbrCompletedAt;

    @Column(name = "fbr_error_message")
    private String fbrErrorMessage;

    @Column(name = "fbr_invoice_number")
    private String fbrInvoiceNumber;

    @Column(name = "fbr_qr_code")
    private String fbrQrCode;

    @Column(name = "currency_code", nullable = false, columnDefinition = "bpchar(3)")
    private String currencyCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SaleItem> items = new LinkedHashSet<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SalePayment> payments = new LinkedHashSet<>();

    public Sale() {
    }

    public UUID getId() {
        return id;
    }

    public String getFbrProviderCode() {
        return fbrProviderCode;
    }

    public void setFbrProviderCode(String fbrProviderCode) {
        this.fbrProviderCode = fbrProviderCode;
    }

    public String getFbrEnvironment() {
        return fbrEnvironment;
    }

    public void setFbrEnvironment(String fbrEnvironment) {
        this.fbrEnvironment = fbrEnvironment;
    }

    public String getFbrStatus() {
        return fbrStatus;
    }

    public void setFbrStatus(String fbrStatus) {
        this.fbrStatus = fbrStatus;
    }

    public String getFbrRequestId() {
        return fbrRequestId;
    }

    public void setFbrRequestId(String fbrRequestId) {
        this.fbrRequestId = fbrRequestId;
    }

    public OffsetDateTime getFbrSubmittedAt() {
        return fbrSubmittedAt;
    }

    public void setFbrSubmittedAt(OffsetDateTime fbrSubmittedAt) {
        this.fbrSubmittedAt = fbrSubmittedAt;
    }

    public OffsetDateTime getFbrCompletedAt() {
        return fbrCompletedAt;
    }

    public void setFbrCompletedAt(OffsetDateTime fbrCompletedAt) {
        this.fbrCompletedAt = fbrCompletedAt;
    }

    public String getFbrErrorMessage() {
        return fbrErrorMessage;
    }

    public void setFbrErrorMessage(String fbrErrorMessage) {
        this.fbrErrorMessage = fbrErrorMessage;
    }

    public String getFbrInvoiceNumber() {
        return fbrInvoiceNumber;
    }

    public void setFbrInvoiceNumber(String fbrInvoiceNumber) {
        this.fbrInvoiceNumber = fbrInvoiceNumber;
    }

    public String getFbrQrCode() {
        return fbrQrCode;
    }

    public void setFbrQrCode(String fbrQrCode) {
        this.fbrQrCode = fbrQrCode;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public void setTerminal(Terminal terminal) {
        this.terminal = terminal;
    }

    public Register getRegister() {
        return register;
    }

    public void setRegister(Register register) {
        this.register = register;
    }

    public RegisterSession getRegisterSession() {
        return registerSession;
    }

    public void setRegisterSession(RegisterSession registerSession) {
        this.registerSession = registerSession;
    }

    public User getCashier() {
        return cashier;
    }

    public void setCashier(User cashier) {
        this.cashier = cashier;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public void setDiscountTotal(BigDecimal discountTotal) {
        this.discountTotal = discountTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public void setTaxTotal(BigDecimal taxTotal) {
        this.taxTotal = taxTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Set<SaleItem> getItems() {
        return items;
    }

    public Set<SalePayment> getPayments() {
        return payments;
    }

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }

    public void addPayment(SalePayment payment) {
        payment.setSale(this);
        payments.add(payment);
    }
}
