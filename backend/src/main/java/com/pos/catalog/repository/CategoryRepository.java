package com.pos.catalog.repository;

import com.pos.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByNameAndParentIsNull(String name);
    boolean existsByNameAndParentId(String name, UUID parentId);
    boolean existsByNameAndParentIsNullAndIdNot(String name, UUID id);
    boolean existsByNameAndParentIdAndIdNot(String name, UUID parentId, UUID id);
}
