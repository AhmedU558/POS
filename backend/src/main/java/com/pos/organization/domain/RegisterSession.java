package com.pos.organization.domain;

import com.pos.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "register_sessions")
public class RegisterSession {

    public static final String STATUS_OPEN = "OPEN";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false)
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @CreationTimestamp
    @Column(name = "opened_at", nullable = false, updatable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(nullable = false, length = 30)
    private String status = STATUS_OPEN;

    @Column(name = "opening_cash", precision = 19, scale = 4)
    private BigDecimal openingCash;

    public RegisterSession() {
    }

    public UUID getId() {
        return id;
    }

    public Register getRegister() {
        return register;
    }

    public User getCashier() {
        return cashier;
    }

    public void setRegister(Register register) {
        this.register = register;
    }

    public void setCashier(User cashier) {
        this.cashier = cashier;
    }

    public OffsetDateTime getOpenedAt() {
        return openedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getOpeningCash() {
        return openingCash;
    }

    public void setOpeningCash(BigDecimal openingCash) {
        this.openingCash = openingCash;
    }

    public boolean isOpen() {
        return STATUS_OPEN.equals(status) && closedAt == null;
    }
}
