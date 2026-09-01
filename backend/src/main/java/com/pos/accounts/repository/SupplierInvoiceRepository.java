package com.pos.accounts.repository;

import com.pos.accounts.domain.SupplierInvoice;
import com.pos.accounts.domain.SupplierInvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, UUID> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumberAndIdNot(String invoiceNumber, UUID id);

    @EntityGraph(attributePaths = "supplier")
    @Query("SELECT i FROM SupplierInvoice i WHERE i.id = :id")
    Optional<SupplierInvoice> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM SupplierInvoice i WHERE i.id = :id")
    Optional<SupplierInvoice> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "supplier")
    @Query("SELECT i FROM SupplierInvoice i WHERE i.status = com.pos.accounts.domain.SupplierInvoiceStatus.OPEN "
            + "AND i.dueDate < :today")
    Page<SupplierInvoice> findOverdue(@Param("today") LocalDate today, Pageable pageable);

    @EntityGraph(attributePaths = "supplier")
    List<SupplierInvoice> findBySupplier_IdOrderByInvoiceDateAscCreatedAtAsc(UUID supplierId);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM SupplierInvoice i WHERE i.status <> com.pos.accounts.domain.SupplierInvoiceStatus.CANCELLED")
    BigDecimal sumTotalInvoiced();

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM SupplierInvoice i WHERE i.status <> com.pos.accounts.domain.SupplierInvoiceStatus.CANCELLED")
    BigDecimal sumPaid();

    @Query("SELECT COALESCE(SUM(i.totalAmount - i.paidAmount), 0) FROM SupplierInvoice i WHERE i.status = com.pos.accounts.domain.SupplierInvoiceStatus.OPEN")
    BigDecimal sumOutstanding();

    @Query("SELECT COALESCE(SUM(i.totalAmount - i.paidAmount), 0) FROM SupplierInvoice i "
            + "WHERE i.status = com.pos.accounts.domain.SupplierInvoiceStatus.OPEN AND i.dueDate < :today")
    BigDecimal sumOverdue(@Param("today") LocalDate today);

    @EntityGraph(attributePaths = "supplier")
    @Query("SELECT i FROM SupplierInvoice i WHERE "
            + "(:query = '' OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "AND (:status IS NULL OR i.status = :status)")
    Page<SupplierInvoice> search(
            @Param("query") String query,
            @Param("status") SupplierInvoiceStatus status,
            Pageable pageable);
}
