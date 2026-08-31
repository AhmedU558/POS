package com.pos.finance.repository;

import com.pos.finance.domain.Budget;
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
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.lines WHERE b.id = :id")
    Optional<Budget> findDetailedById(@Param("id") UUID id);

    @Query("SELECT b FROM Budget b WHERE b.store.id IN :storeIds")
    Page<Budget> search(@Param("storeIds") List<UUID> storeIds, Pageable pageable);
}
