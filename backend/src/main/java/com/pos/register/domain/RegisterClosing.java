package com.pos.register.domain;

import com.pos.organization.domain.RegisterSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "register_closings")
public class RegisterClosing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_session_id", nullable = false, unique = true)
    private RegisterSession registerSession;

    @Column(name = "z_report_number", nullable = false, unique = true, length = 50)
    private String zReportNumber;

    @Column(length = 255)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public RegisterClosing() {
    }

    public UUID getId() {
        return id;
    }

    public RegisterSession getRegisterSession() {
        return registerSession;
    }

    public void setRegisterSession(RegisterSession registerSession) {
        this.registerSession = registerSession;
    }

    public String getZReportNumber() {
        return zReportNumber;
    }

    public void setZReportNumber(String zReportNumber) {
        this.zReportNumber = zReportNumber;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
