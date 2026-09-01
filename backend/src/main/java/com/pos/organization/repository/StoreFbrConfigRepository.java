package com.pos.organization.repository;

import com.pos.organization.domain.StoreFbrConfig;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StoreFbrConfigRepository extends CrudRepository<StoreFbrConfig, UUID> {
}
