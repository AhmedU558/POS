package com.pos.organization.repository;

import com.pos.organization.domain.RegisterSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RegisterSessionRepository extends JpaRepository<RegisterSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RegisterSession s JOIN FETCH s.register r JOIN FETCH r.store JOIN FETCH r.terminal WHERE s.id = :id")
    Optional<RegisterSession> findByIdForUpdate(@Param("id") UUID id);
    boolean existsByRegister_IdAndStatus(UUID registerId, String status);

    @Query("SELECT s FROM RegisterSession s JOIN FETCH s.register r JOIN FETCH r.store JOIN FETCH r.terminal JOIN FETCH s.cashier WHERE s.id = :id")
    Optional<RegisterSession> findDetailedById(@Param("id") UUID id);

    /**
     * The session a cashier currently has open, if any.
     *
     * <p>Ordering is defensive rather than expected: a unique index keeps one open session per
     * register, but nothing stops one cashier holding open sessions on two registers, so the most
     * recently opened one is the one they are standing at.
     */
    @Query("SELECT s FROM RegisterSession s JOIN FETCH s.register r JOIN FETCH r.store JOIN FETCH r.terminal"
            + " JOIN FETCH s.cashier c WHERE c.id = :cashierId AND s.status = :status"
            + " ORDER BY s.openedAt DESC LIMIT 1")
    Optional<RegisterSession> findCurrentForCashier(@Param("cashierId") UUID cashierId, @Param("status") String status);
}
