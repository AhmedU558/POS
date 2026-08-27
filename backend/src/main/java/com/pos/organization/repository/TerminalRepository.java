package com.pos.organization.repository;

import com.pos.organization.domain.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, UUID> {
    List<Terminal> findByStoreId(UUID storeId);
}
