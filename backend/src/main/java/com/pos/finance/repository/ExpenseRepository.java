package com.pos.finance.repository;

import com.pos.finance.domain.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @Query("SELECT e FROM Expense e WHERE e.store.id IN :storeIds")
    Page<Expense> search(@Param("storeIds") List<UUID> storeIds, Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.store.id = :storeId AND e.expenseDate >= :startDate AND e.expenseDate <= :endDate")
    List<Expense> findByStoreAndDateRange(
            @Param("storeId") UUID storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
