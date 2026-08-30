package com.pos.sales.repository;

import com.pos.sales.domain.Sale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @EntityGraph(attributePaths = {"items", "items.product", "payments", "payments.paymentMethod", "store"})
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findDetailedById(@Param("id") UUID id);

    @Query(value = "SELECT nextval('sale_receipt_number_seq')", nativeQuery = true)
    long nextReceiptSequence();
}
