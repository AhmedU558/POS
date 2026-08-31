package com.pos.inventory.repository;

import com.pos.inventory.domain.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ib FROM InventoryBalance ib WHERE ib.product.id = :productId AND ib.store.id = :storeId")
    Optional<InventoryBalance> findByProductIdAndStoreIdForUpdate(@Param("productId") UUID productId, @Param("storeId") UUID storeId);

    /**
     * Inserts a zero balance when the pair is new. Concurrent first receipts do not abort
     * the caller: the unique constraint is absorbed here, then {@link #findByProductIdAndStoreIdForUpdate}
     * locks the surviving row.
     */
    @Modifying
    @Query(value = """
            INSERT INTO inventory_balances (id, product_id, store_id, quantity, last_updated_at)
            VALUES (gen_random_uuid(), :productId, :storeId, 0, NOW())
            ON CONFLICT (product_id, store_id) DO NOTHING
            """, nativeQuery = true)
    void insertZeroBalanceIfAbsent(@Param("productId") UUID productId, @Param("storeId") UUID storeId);

    @Query("SELECT ib FROM InventoryBalance ib WHERE " +
           "(:storeId IS NULL OR ib.store.id = :storeId) AND " +
           "(:categoryId IS NULL OR ib.product.category.id = :categoryId) AND " +
           "(:query = '' OR LOWER(ib.product.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(ib.product.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<InventoryBalance> searchBalances(@Param("storeId") UUID storeId,
                                          @Param("categoryId") UUID categoryId,
                                          @Param("query") String query,
                                          Pageable pageable);

    @EntityGraph(attributePaths = {"product", "store"})
    @Query("SELECT ib FROM InventoryBalance ib WHERE ib.store.id = :storeId AND ib.quantity <= ib.product.minStock")
    List<InventoryBalance> findBelowMinimum(@Param("storeId") UUID storeId);

    @EntityGraph(attributePaths = {"product", "store"})
    @Query("SELECT ib FROM InventoryBalance ib WHERE ib.store.id = :storeId AND (:lowStockOnly = false OR ib.quantity <= ib.product.minStock)")
    Page<InventoryBalance> searchReportBalances(
            @Param("storeId") UUID storeId,
            @Param("lowStockOnly") boolean lowStockOnly,
            Pageable pageable);
}