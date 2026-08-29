package com.pos.catalog.repository;

import com.pos.catalog.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {
    List<ProductPrice> findByProductIdOrderByEffectiveFromDesc(UUID productId);
}
