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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A named bundle of permissions.
 *
 * <p>The six roles defined by the approved roles-and-permissions.md are seeded as reference data.
 * A role is a convenience for granting permissions; authorization decisions are made on the
 * permission codes it carries, per REST API Specification section 4.3.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /**
     * No cascade: revoking a grant must never delete the {@link Permission} itself, which is
     * shared reference data.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    /** Required by JPA. */
    protected Role() {}

    public Role(String name, String description) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public void grant(Permission permission) {
        permissions.add(Objects.requireNonNull(permission, "permission"));
    }

    public void revoke(Permission permission) {
        permissions.remove(permission);
    }

    /** The permission codes this role carries, ready for an authorization check. */
    public Set<String> permissionCodes() {
        return permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Compares through the getter so an uninitialized proxy is resolved first. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Role role && name != null && name.equals(role.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return "Role[" + name + "]";
    }
}
