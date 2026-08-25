package com.pos.bootstrap.repository;

import com.pos.bootstrap.domain.BootstrapCompletion;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Access to the bootstrap completion marker.
 *
 * <p>Extends {@link Repository} rather than {@code JpaRepository} so no delete method exists to
 * call. Removing the marker would re-arm first-administrator provisioning.
 *
 * <p>This is an application-level restriction, not a database one: a SQL session can still delete
 * the row. It removes the one-line route, not every route. Restricting that further belongs with
 * the least-privilege database role already recorded in deferred-work.md.
 */
public interface BootstrapCompletionRepository extends Repository<BootstrapCompletion, UUID> {

    BootstrapCompletion save(BootstrapCompletion completion);

    Optional<BootstrapCompletion> findFirstBy();

    /** Forces the insert so a concurrent instance loses on the unique constraint immediately. */
    void flush();

    long count();
}
