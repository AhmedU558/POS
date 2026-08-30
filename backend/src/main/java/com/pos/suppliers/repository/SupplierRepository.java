package com.pos.suppliers.repository;

import com.pos.suppliers.domain.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsBySupplierCode(String supplierCode);

    boolean existsBySupplierCodeAndIdNot(String supplierCode, UUID id);

    @Query("SELECT s FROM Supplier s WHERE "
            + "(:query IS NULL OR LOWER(s.supplierCode) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(COALESCE(s.phone, '')) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(COALESCE(s.email, '')) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "AND (:isActive IS NULL OR s.active = :isActive)")
    Page<Supplier> search(
            @Param("query") String query,
            @Param("isActive") Boolean isActive,
            Pageable pageable);
}
