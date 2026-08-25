package com.pos.bootstrap;

import com.pos.AbstractIntegrationTest;
import com.pos.bootstrap.config.BootstrapProperties;
import com.pos.bootstrap.repository.BootstrapCompletionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent startup, exercised through real threads against the real database.
 *
 * <p>The guarantee under test is a PostgreSQL unique constraint, not application synchronization,
 * so the test has to produce genuine concurrent transactions. A mocked or single-threaded
 * simulation would prove nothing: it would pass equally well against a design that used an
 * in-process lock, which is exactly the design ADR-015 rejected.
 */
class BootstrapConcurrencyTests extends AbstractIntegrationTest {

    private static final String USERNAME = "grace.hopper";
    private static final int INSTANCES = 4;

    @Autowired private BootstrapProperties properties;
    @Autowired private BootstrapRunner runner;
    @Autowired private BootstrapCompletionRepository completionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void configure() {
        clean();
        properties.setEnabled(true);
        properties.setUsername(USERNAME);
        properties.setFirstName("Grace");
        properties.setLastName("Hopper");
        properties.setPassword("a-sufficiently-long-bootstrap-password");
        properties.setPasswordFile(null);
    }

    @AfterEach
    void clean() {
        properties.setEnabled(false);
        properties.setUsername(null);
        properties.setFirstName(null);
        properties.setLastName(null);
        properties.setEmail(null);
        properties.setPassword(null);
        jdbcTemplate.update("DELETE FROM bootstrap_completions");
        jdbcTemplate.update(
                "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = ?)",
                USERNAME);
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", USERNAME);
    }

    // 7. Two simultaneous bootstrap attempts -> exactly one succeeds.
    @Test
    void simultaneousInstancesProduceExactlyOneAdministrator() throws Exception {
        List<Outcome> outcomes = startTogether();

        assertThat(outcomes)
                .as("every instance must finish; none may crash-loop")
                .allSatisfy(outcome -> assertThat(outcome.failure()).isNull());

        assertThat(outcomes.stream().filter(Outcome::created).count())
                .as("exactly one instance may win the race")
                .isEqualTo(1);

        assertThat(administratorCount()).isEqualTo(1);
        assertThat(completionRepository.count()).isEqualTo(1);
    }

    @Test
    void losingInstancesContinueStartingRatherThanFailing() throws Exception {
        List<Outcome> outcomes = startTogether();

        List<Outcome> losers = outcomes.stream().filter(outcome -> !outcome.created()).toList();

        assertThat(losers).hasSize(INSTANCES - 1);
        // A losing instance returning normally is what keeps a rolling deployment from
        // crash-looping every replica that did not happen to win.
        assertThat(losers).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
    }

    @Test
    void exactlyOneAuditEventIsRecordedForTheOneAdministrator() throws Exception {
        startTogether();

        // Asserted rather than fetched directly: if every instance failed, queryForObject would
        // throw and hide the real failure behind an EmptyResultDataAccessException.
        List<java.util.UUID> ids =
                jdbcTemplate.queryForList(
                        "SELECT id FROM users WHERE username = ?", java.util.UUID.class, USERNAME);
        assertThat(ids).hasSize(1);
        java.util.UUID adminId = ids.get(0);
        Long events =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE action = ? AND entity_id = ?",
                        Long.class,
                        com.pos.bootstrap.service.FirstAdministratorBootstrap.ADMINISTRATOR_CREATED,
                        adminId);

        // The losers' transactions rolled back, taking their audit rows with them.
        assertThat(events).isEqualTo(1L);
    }

    /** Releases every thread from a barrier so the attempts genuinely overlap. */
    private List<Outcome> startTogether() throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(INSTANCES);
        ExecutorService pool = Executors.newFixedThreadPool(INSTANCES);
        try {
            List<Callable<Outcome>> instances =
                    java.util.stream.IntStream.range(0, INSTANCES)
                            .<Callable<Outcome>>mapToObj(
                                    ignored ->
                                            () -> {
                                                startLine.await(10, TimeUnit.SECONDS);
                                                try {
                                                    return new Outcome(runner.runBootstrap(), null);
                                                } catch (RuntimeException ex) {
                                                    return new Outcome(false, ex);
                                                }
                                            })
                            .toList();

            List<Future<Outcome>> futures = pool.invokeAll(instances, 60, TimeUnit.SECONDS);
            return futures.stream().map(BootstrapConcurrencyTests::resolve).toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private static Outcome resolve(Future<Outcome> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            return new Outcome(false, ex);
        }
    }

    private long administratorCount() {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE username = ?", Long.class, USERNAME);
        return count == null ? 0L : count;
    }

    private record Outcome(boolean created, Exception failure) {}
}
