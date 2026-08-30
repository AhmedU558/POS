package com.pos.sales.repository;

import com.pos.sales.domain.CashTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, UUID> {
}
