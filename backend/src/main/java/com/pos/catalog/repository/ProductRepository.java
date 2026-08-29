package com.pos.catalog.repository;

import com.pos.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    
    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);
    
    boolean existsBySkuAndIdNot(String sku, UUID id);

    @Query("SELECT p FROM Product p WHERE " +
           "(:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:brandId IS NULL OR p.brand.id = :brandId) AND " +
           "(:isActive IS NULL OR p.isActive = :isActive)")
    Page<Product> searchProducts(@Param("query") String query,
                                 @Param("categoryId") UUID categoryId,
                                 @Param("brandId") UUID brandId,
                                 @Param("isActive") Boolean isActive,
                                 Pageable pageable);
}
