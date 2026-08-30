package com.pos.accounts.repository;

import com.pos.accounts.domain.SupplierPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, UUID> {

    @EntityGraph(attributePaths = "invoice")
    Page<SupplierPayment> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "invoice")
    Page<SupplierPayment> findByInvoice_Id(UUID invoiceId, Pageable pageable);

    @EntityGraph(attributePaths = "invoice")
    @Query("SELECT p FROM SupplierPayment p WHERE p.invoice.supplier.id = :supplierId "
            + "ORDER BY p.paymentDate ASC, p.createdAt ASC")
    List<SupplierPayment> findBySupplierId(@Param("supplierId") UUID supplierId);
}
