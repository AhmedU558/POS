package com.pos.bootstrap;

import com.pos.bootstrap.service.FirstAdministratorBootstrap;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Runs first-administrator provisioning at startup.
 *
 * <p>This class exists solely to sit <em>outside</em> the provisioning transaction. A PostgreSQL
 * constraint violation aborts the transaction it occurs in, so the losing instance in a race
 * cannot catch its own violation and carry on — the transaction has to roll back first. Catching
 * it here, one level out, is what lets that instance continue starting instead of crash-looping.
 */
@Component
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    /**
     * The only violation that means "provisioning has already happened".
     *
     * <p>Named in {@code V5__create_bootstrap_completions.sql} precisely so it can be recognised
     * here rather than inferred from a message.
     */
    private static final String SINGLETON_CONSTRAINT = "uk_bootstrap_completions_singleton";

    private final FirstAdministratorBootstrap bootstrap;

    public BootstrapRunner(FirstAdministratorBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void run(ApplicationArguments args) {
        runBootstrap();
    }

    /**
     * Separated from {@link #run} so tests can exercise a restart by calling it again, which is
     * the same code path a real restart takes.
     *
     * @return true when this call created the administrator
     */
    public boolean runBootstrap() {
        try {
            boolean created = bootstrap.bootstrapIfEnabled();
            if (created) {
                log.info("First administrator provisioned. Bootstrap is now permanently complete.");
            }
            return created;
        } catch (DataIntegrityViolationException ex) {
            if (!isAlreadyCompleted(ex)) {
                // Any other integrity failure -- a username already taken, a missing reference --
                // means provisioning genuinely failed. Swallowing it would let the application
                // start reporting that bootstrap had already happened when it never has.
                throw ex;
            }
            // Either another instance won the race, or bootstrap completed in an earlier run and
            // configuration was left switched on. Both are ordinary, not faults: the database has
            // already guaranteed exactly one administrator was created.
            // WARN, not INFO: a plaintext administrator credential is still mounted in the
            // environment of every replica, and INFO is routinely filtered in production.
            log.warn(
                    "Bootstrap did not run: provisioning has already completed. The configured"
                            + " administrator was NOT created. Remove app.bootstrap.* configuration"
                            + " and revoke the supplied credential.");
            return false;
        }
    }

    /**
     * Walks the cause chain for the singleton constraint, which Hibernate names for us.
     *
     * <p>Cycle-safe by identity rather than by comparing each link to its own cause: a chain can
     * loop over several hops, and a startup path that hangs is worse than one that fails.
     */
    private static boolean isAlreadyCompleted(Throwable throwable) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = throwable; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && SINGLETON_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
