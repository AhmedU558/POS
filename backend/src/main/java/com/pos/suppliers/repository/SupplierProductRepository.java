package com.pos.suppliers.repository;

import com.pos.suppliers.domain.SupplierProduct;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, UUID> {

    @EntityGraph(attributePaths = "product")
    List<SupplierProduct> findBySupplierIdOrderByProductSkuAsc(UUID supplierId);

    void deleteBySupplierId(UUID supplierId);
}
