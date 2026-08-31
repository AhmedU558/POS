package com.pos.orders.repository;

import com.pos.orders.domain.OnlineOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnlineOrderRepository extends JpaRepository<OnlineOrder, UUID> {
    
    @Query("SELECT o FROM OnlineOrder o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<OnlineOrder> findDetailedById(@Param("id") UUID id);
    
    @Query("SELECT o FROM OnlineOrder o WHERE o.store.id IN :storeIds")
    Page<OnlineOrder> search(@Param("storeIds") List<UUID> storeIds, Pageable pageable);
    
    Optional<OnlineOrder> findByChannelAndExternalOrderId(String channel, String externalOrderId);
}
