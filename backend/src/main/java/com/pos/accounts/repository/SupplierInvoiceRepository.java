package com.pos.accounts.repository;

import com.pos.accounts.domain.SupplierInvoice;
import com.pos.accounts.domain.SupplierInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, UUID> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumberAndIdNot(String invoiceNumber, UUID id);

    @EntityGraph(attributePaths = "supplier")
    @Query("SELECT i FROM SupplierInvoice i WHERE i.id = :id")
    Optional<SupplierInvoice> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "supplier")
    @Query("SELECT i FROM SupplierInvoice i WHERE "
            + "(:query IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "AND (:status IS NULL OR i.status = :status)")
    Page<SupplierInvoice> search(
            @Param("query") String query,
            @Param("status") SupplierInvoiceStatus status,
            Pageable pageable);
}
