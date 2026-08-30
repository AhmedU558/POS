package com.pos.inventory.repository;

import com.pos.inventory.domain.StockAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockAlertRepository extends JpaRepository<StockAlert, UUID> {

    Optional<StockAlert> findByStoreIdAndProductIdAndAlertTypeAndBatchIsNull(UUID storeId, UUID productId, String alertType);

    Optional<StockAlert> findByStoreIdAndBatchIdAndAlertType(UUID storeId, UUID batchId, String alertType);

    List<StockAlert> findByStoreIdAndAlertType(UUID storeId, String alertType);

    @EntityGraph(attributePaths = {"product", "store", "batch"})
    @Query("""
            SELECT a FROM StockAlert a
            WHERE a.store.id = :storeId
              AND (:alertType IS NULL OR a.alertType = :alertType)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<StockAlert> search(
            @Param("storeId") UUID storeId,
            @Param("alertType") String alertType,
            @Param("status") String status,
            Pageable pageable);
}
