package com.pos.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * An atomic permission code.
 *
 * <p>REST API Specification section 4.3 requires authorization to be expressed as stable codes
 * such as {@code USER_READ} rather than role names, so that a role's reach can change without
 * rewriting every check. Codes are reference data, seeded by migration.
 *
 * <p>Leaf of the identity graph: a permission holds no association of its own.
 */
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "description", length = 255)
    private String description;

    /** Required by JPA. */
    protected Permission() {}

    public Permission(String code, String description) {
        this.code = Objects.requireNonNull(code, "code");
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Identity is the business key, not the surrogate id: permissions live in {@code Set}s and
     * are compared before they are ever persisted.
     *
     * <p>Compares through the getter, never the field. The other side may be an uninitialized
     * Hibernate proxy whose backing field is still null, which would make a field comparison
     * silently return false and let a duplicate into the set.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Permission permission
                && code != null
                && code.equals(permission.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

    @Override
    public String toString() {
        return "Permission[" + code + "]";
    }
}
