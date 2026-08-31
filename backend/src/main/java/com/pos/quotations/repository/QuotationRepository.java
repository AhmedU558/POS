package com.pos.quotations.repository;

import com.pos.quotations.domain.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
    
    @Query("SELECT q FROM Quotation q LEFT JOIN FETCH q.items i LEFT JOIN FETCH i.product WHERE q.id = :id")
    Optional<Quotation> findDetailedById(@Param("id") UUID id);
    
    @Query("SELECT q FROM Quotation q WHERE q.store.id IN :storeIds")
    Page<Quotation> search(@Param("storeIds") List<UUID> storeIds, Pageable pageable);

    @Query(value = "SELECT nextval('sale_receipt_number_seq')", nativeQuery = true)
    long nextQuotationSequence();
}
