package com.pos.customers.repository;

import com.pos.customers.domain.CustomerCredit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerCreditRepository extends JpaRepository<CustomerCredit, UUID> {

    Optional<CustomerCredit> findByCustomerId(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CustomerCredit c WHERE c.customer.id = :customerId")
    Optional<CustomerCredit> findByCustomerIdForUpdate(@Param("customerId") UUID customerId);

    /**
     * Inserts a zero-balance account when the customer has none. Concurrent first posts do not
     * abort: the unique constraint is absorbed here, then {@link #findByCustomerIdForUpdate}
     * locks the surviving row.
     */
    @Modifying
    @Query(value = """
            INSERT INTO customer_credits (id, customer_id, balance, currency_code, status, created_at, updated_at)
            VALUES (gen_random_uuid(), :customerId, 0, :currencyCode, 'ACTIVE', NOW(), NOW())
            ON CONFLICT (customer_id) DO NOTHING
            """, nativeQuery = true)
    void insertZeroAccountIfAbsent(@Param("customerId") UUID customerId, @Param("currencyCode") String currencyCode);
}
