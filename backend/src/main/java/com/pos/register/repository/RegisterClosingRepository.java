package com.pos.register.repository;

import com.pos.register.domain.RegisterClosing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RegisterClosingRepository extends JpaRepository<RegisterClosing, UUID> {

    Optional<RegisterClosing> findByRegisterSession_Id(UUID sessionId);

    @Query(value = "SELECT nextval('z_report_number_seq')", nativeQuery = true)
    long nextZReportSequence();
}
