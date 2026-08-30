package com.pos.customers.repository;

import com.pos.customers.domain.CustomerCreditTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerCreditTransactionRepository extends JpaRepository<CustomerCreditTransaction, UUID> {

    Page<CustomerCreditTransaction> findByCustomerCreditIdOrderByCreatedAtDesc(UUID customerCreditId, Pageable pageable);
}
