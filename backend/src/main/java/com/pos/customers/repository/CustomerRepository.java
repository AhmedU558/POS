package com.pos.customers.repository;

import com.pos.customers.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByCustomerCode(String customerCode);

    boolean existsByCustomerCodeAndIdNot(String customerCode, UUID id);

    @Query("SELECT c FROM Customer c WHERE "
            + "(:query IS NULL OR LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(COALESCE(c.phone, '')) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "AND (:isActive IS NULL OR c.active = :isActive)")
    Page<Customer> search(
            @Param("query") String query,
            @Param("isActive") Boolean isActive,
            Pageable pageable);
}
