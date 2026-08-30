package com.pos.sales.repository;

import com.pos.sales.domain.Sale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @EntityGraph(attributePaths = {"items", "items.product", "payments", "payments.paymentMethod", "store", "cashier", "customer"})
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findDetailedById(@Param("id") UUID id);

    @Query(value = "SELECT nextval('sale_receipt_number_seq')", nativeQuery = true)
    long nextReceiptSequence();

    @EntityGraph(attributePaths = {"store", "cashier", "customer"})
    @Query("SELECT s FROM Sale s WHERE s.store.id IN :storeIds "
            + "AND (:query = '' OR LOWER(s.receiptNumber) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "AND (:status = '' OR s.status = :status) "
            + "AND (:customerId IS NULL OR s.customer.id = :customerId) "
            + "AND (:cashierId IS NULL OR s.cashier.id = :cashierId) "
            + "AND s.createdAt >= :from AND s.createdAt <= :to")
    Page<Sale> search(
            @Param("storeIds") Collection<UUID> storeIds,
            @Param("query") String query,
            @Param("status") String status,
            @Param("customerId") UUID customerId,
            @Param("cashierId") UUID cashierId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);
}
