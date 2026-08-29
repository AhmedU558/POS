package com.pos.catalog.repository;

import com.pos.catalog.entity.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, UUID> {
    List<ProductBarcode> findByProductId(UUID productId);
    boolean existsByBarcode(String barcode);
}
