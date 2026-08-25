package com.pos.users.repository;

import com.pos.users.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Access to permission reference data, keyed on the stable code from API spec section 4.3.
 *
 * <p>Deliberately minimal: lookups are added by the story that needs them, so no untested query
 * sits here waiting to be wrong the first time someone calls it.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);
}
