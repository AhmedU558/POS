package com.pos.promotions.repository;

import com.pos.promotions.domain.Promotion;
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
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
    
    @Query("SELECT p FROM Promotion p LEFT JOIN FETCH p.rules WHERE p.id = :id")
    Optional<Promotion> findDetailedById(@Param("id") UUID id);
    
    @Query("SELECT p FROM Promotion p WHERE p.store.id IN :storeIds")
    Page<Promotion> search(@Param("storeIds") List<UUID> storeIds, Pageable pageable);

    @Query("SELECT p FROM Promotion p LEFT JOIN FETCH p.rules WHERE p.store.id = :storeId AND p.active = true AND p.startDate <= :now AND p.endDate >= :now ORDER BY p.priority DESC")
    List<Promotion> findActiveByStore(@Param("storeId") UUID storeId, @Param("now") java.time.OffsetDateTime now);
}
