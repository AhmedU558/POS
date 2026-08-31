package com.pos.sales.repository;

import com.pos.sales.domain.CashTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, UUID> {

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashTransaction c WHERE c.registerSession.id = :sessionId AND c.transactionType = :type")
    BigDecimal sumAmount(@Param("sessionId") UUID sessionId, @Param("type") String type);
}
