package com.pos.inventory.repository;

import com.pos.inventory.domain.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ib FROM InventoryBalance ib WHERE ib.product.id = :productId AND ib.store.id = :storeId")
    Optional<InventoryBalance> findByProductIdAndStoreIdForUpdate(@Param("productId") UUID productId, @Param("storeId") UUID storeId);

    @Query("SELECT ib FROM InventoryBalance ib WHERE " +
           "(:storeId IS NULL OR ib.store.id = :storeId) AND " +
           "(:categoryId IS NULL OR ib.product.category.id = :categoryId) AND " +
           "(:query IS NULL OR LOWER(ib.product.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(ib.product.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<InventoryBalance> searchBalances(@Param("storeId") UUID storeId,
                                          @Param("categoryId") UUID categoryId,
                                          @Param("query") String query,
                                          Pageable pageable);
}