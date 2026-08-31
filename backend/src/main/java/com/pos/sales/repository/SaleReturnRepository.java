package com.pos.sales.repository;

import com.pos.sales.domain.SaleReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SaleReturnRepository extends JpaRepository<SaleReturn, UUID> {
}
