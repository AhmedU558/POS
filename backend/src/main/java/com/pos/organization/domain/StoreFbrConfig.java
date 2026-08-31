package com.pos.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_fbr_configs")
public class StoreFbrConfig {

    @Id
    private UUID storeId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "environment", nullable = false, length = 20)
    private String environment = "sandbox";

    @Column(name = "ntn", length = 20)
    private String ntn;

    @Column(name = "strn", length = 30)
    private String strn;

    @Column(name = "pos_id", length = 50)
    private String posId;

    @Column(name = "encrypted_secret")
    private String encryptedSecret;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public StoreFbrConfig() {}

    public StoreFbrConfig(Store store) {
        this.store = store;
        this.storeId = store.getId();
    }

    public UUID getStoreId() {
        return storeId;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
        this.storeId = store.getId();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getNtn() {
        return ntn;
    }

    public void setNtn(String ntn) {
        this.ntn = ntn;
    }

    public String getStrn() {
        return strn;
    }

    public void setStrn(String strn) {
        this.strn = strn;
    }

    public String getPosId() {
        return posId;
    }

    public void setPosId(String posId) {
        this.posId = posId;
    }

    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public void setEncryptedSecret(String encryptedSecret) {
        this.encryptedSecret = encryptedSecret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
