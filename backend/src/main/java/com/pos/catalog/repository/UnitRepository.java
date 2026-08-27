package com.pos.catalog.repository;

import com.pos.catalog.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
    boolean existsByCode(String code);
    boolean existsByName(String name);
    boolean existsByCodeAndIdNot(String code, UUID id);
    boolean existsByNameAndIdNot(String name, UUID id);
}
