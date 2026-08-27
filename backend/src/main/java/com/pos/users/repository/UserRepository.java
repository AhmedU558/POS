package com.pos.users.repository;

import com.pos.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Access to application users. */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);

    /**
     * Loads a user together with roles and their permissions in one query.
     *
     * <p>Authorization needs the whole graph. Without the fetch joins this costs a query per role,
     * and any read after the transaction closes fails outright.
     */
    @Query(
            "SELECT DISTINCT u FROM User u"
                    + " LEFT JOIN FETCH u.roles r"
                    + " LEFT JOIN FETCH r.permissions"
                    + " WHERE u.username = :username")
    Optional<User> findByUsernameWithRolesAndPermissions(@Param("username") String username);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.stores s WHERE u.id = :userId AND s.id = :storeId")
    boolean hasStoreAccess(@Param("userId") UUID userId, @Param("storeId") UUID storeId);
}
