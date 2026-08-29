package com.pos.inventory.repository;

import com.pos.inventory.domain.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
    Page<InventoryTransaction> findByProductIdAndStoreIdOrderByCreatedAtDesc(UUID productId, UUID storeId, Pageable pageable);
}
