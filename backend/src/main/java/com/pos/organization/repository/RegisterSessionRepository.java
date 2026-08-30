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
}
