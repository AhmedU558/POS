package com.pos.purchases.repository;

import com.pos.purchases.domain.PurchaseOrder;
import com.pos.purchases.domain.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    boolean existsByPoNumber(String poNumber);

    boolean existsByPoNumberAndIdNot(String poNumber, UUID id);

    @EntityGraph(attributePaths = {"supplier", "items", "items.product"})
    @Query("SELECT o FROM PurchaseOrder o WHERE o.id = :id")
    Optional<PurchaseOrder> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "supplier")
    @Query("SELECT o FROM PurchaseOrder o WHERE "
            + "(:query IS NULL OR LOWER(o.poNumber) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "AND (:status IS NULL OR o.status = :status)")
    Page<PurchaseOrder> search(
            @Param("query") String query,
            @Param("status") PurchaseOrderStatus status,
            Pageable pageable);
}
