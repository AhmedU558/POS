package com.pos.purchases.repository;

import com.pos.purchases.domain.GoodsReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    @EntityGraph(attributePaths = {"purchaseOrder", "store", "items", "items.product"})
    @Query("SELECT r FROM GoodsReceipt r WHERE r.id = :id")
    Optional<GoodsReceipt> findDetailedById(@Param("id") UUID id);
}
