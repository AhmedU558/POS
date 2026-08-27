package com.pos.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An application user.
 *
 * <p>Columns map one-to-one onto the {@code users} table defined in Database Design & ERD
 * Specification section 6.1. Timestamps are Hibernate-managed rather than database-triggered:
 * the baseline schema defaults {@code updated_at} on insert but has no update trigger, so
 * without this the column would silently go stale.
 *
 * <p>The password hash is deliberately absent from {@link #toString()}. REST API Specification
 * section 29 forbids returning or logging password hashes, and a careless log line is the most
 * likely way one escapes.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Deactivation rather than deletion, per Database Design section 3 and AUTH-005: historical
     * transactions must keep pointing at a real user row.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * True while the account holds a credential its owner did not choose — issued at bootstrap or
     * reset by an administrator (AMD-001). Cleared only by a successful password change; there is
     * deliberately no setter, so no generic update path can clear it.
     */
    @Column(name = "is_password_change_required", nullable = false)
    private boolean passwordChangeRequired = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * No cascade: removing a user clears its {@code user_roles} rows but must never delete the
     * shared {@link Role} reference data.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_stores",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "store_id"))
    private Set<com.pos.organization.domain.Store> stores = new LinkedHashSet<>();

    @Column(name = "credentials_changed_at", nullable = false)
    private Instant credentialsChangedAt = Instant.now();

    /** Required by JPA. */
    protected User() {}

    public User(String username, String passwordHash, String firstName, String lastName) {
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.firstName = firstName;
        this.lastName = lastName;
        this.credentialsChangedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Belt and braces against accidental serialization. Entities must never be returned from a
     * controller (AGENTS.md), but if one ever is, the hash must not travel with it
     * (REST API Specification section 29).
     */
    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    /** Marks the account as holding a credential the holder must replace before using it. */
    public void requirePasswordChange() {
        this.passwordChangeRequired = true;
    }

    /**
     * Replaces the credential and clears the rotation requirement together.
     * Updates credentialsChangedAt to invalidate existing tokens.
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.passwordChangeRequired = false;
        this.credentialsChangedAt = Instant.now();
    }

    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public void assignRole(Role role) {
        roles.add(Objects.requireNonNull(role, "role"));
    }

    public void removeRole(Role role) {
        roles.remove(role);
    }

    public Set<com.pos.organization.domain.Store> getStores() {
        return Collections.unmodifiableSet(stores);
    }

    public void assignStore(com.pos.organization.domain.Store store) {
        stores.add(Objects.requireNonNull(store, "store"));
    }

    public void removeStore(com.pos.organization.domain.Store store) {
        stores.remove(store);
    }

    /**
     * Every permission code this user holds, flattened across their roles.
     *
     * <p>Computed here so no caller re-derives it and risks deriving it differently.
     *
     * <p><strong>This is not an access decision.</strong> It deliberately ignores
     * {@link #isActive()}: whether an account may be used at all is an authentication concern,
     * settled before a token is ever issued. A caller that consults this set without having
     * checked {@code isActive} will happily authorize a deactivated user.
     */
    public Set<String> permissionCodes() {
        return roles.stream()
                .flatMap(role -> role.permissionCodes().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Compares through the getter so an uninitialized proxy is resolved first. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof User user && username != null && username.equals(user.getUsername());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(username);
    }

    /** Never include the password hash. */
    @Override
    public String toString() {
        return "User[" + username + "]";
    }
}
