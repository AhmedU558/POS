package com.pos.inventory.repository;

import com.pos.inventory.domain.InventoryBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b WHERE b.product.id = :productId AND b.store.id = :storeId AND b.batchNumber = :batchNumber")
    Optional<InventoryBatch> findByProductIdAndStoreIdAndBatchNumberForUpdate(
            @Param("productId") UUID productId,
            @Param("storeId") UUID storeId,
            @Param("batchNumber") String batchNumber);

    /**
     * Inserts a zero-quantity lot when the triple is new. Concurrent first receipts
     * of the same lot do not abort: the unique constraint is absorbed here, then
     * {@link #findByProductIdAndStoreIdAndBatchNumberForUpdate} locks the surviving row.
     */
    @Modifying
    @Query(value = """
            INSERT INTO inventory_batches (
                id, product_id, store_id, batch_number, quantity,
                expiration_date, manufacturing_date, created_at)
            VALUES (
                gen_random_uuid(), :productId, :storeId, :batchNumber, 0,
                :expirationDate, :manufacturingDate, NOW())
            ON CONFLICT (product_id, store_id, batch_number) DO NOTHING
            """, nativeQuery = true)
    void insertZeroBatchIfAbsent(
            @Param("productId") UUID productId,
            @Param("storeId") UUID storeId,
            @Param("batchNumber") String batchNumber,
            @Param("expirationDate") LocalDate expirationDate,
            @Param("manufacturingDate") LocalDate manufacturingDate);

    @EntityGraph(attributePaths = {"product", "store"})
    @Query("SELECT b FROM InventoryBatch b WHERE b.store.id = :storeId AND (:productId IS NULL OR b.product.id = :productId)")
    Page<InventoryBatch> searchBatches(
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"product", "store"})
    @Query("SELECT b FROM InventoryBatch b WHERE b.store.id = :storeId AND b.expirationDate IS NOT NULL AND b.expirationDate <= :horizon")
    Page<InventoryBatch> findExpiringOnOrBefore(
            @Param("storeId") UUID storeId,
            @Param("horizon") LocalDate horizon,
            Pageable pageable);
}
