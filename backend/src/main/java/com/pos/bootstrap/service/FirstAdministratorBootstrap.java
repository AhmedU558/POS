package com.pos.bootstrap.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.bootstrap.config.BootstrapProperties;
import com.pos.bootstrap.domain.BootstrapCompletion;
import com.pos.bootstrap.repository.BootstrapCompletionRepository;
import com.pos.users.domain.Role;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator, once, from operator-supplied credentials.
 *
 * <p>Everything this method does belongs to a single transaction, deliberately. The account, the
 * completion marker and the audit record commit together or not at all, so a failure part-way
 * through cannot leave a marker claiming a provisioning that never finished — which would lock the
 * system out of ever creating its first administrator.
 *
 * <p>Constraint violations are allowed to propagate. A PostgreSQL constraint violation aborts the
 * transaction, so catching one here and continuing is not possible; the caller
 * ({@code BootstrapRunner}) handles it outside the transactional boundary.
 */
@Service
public class FirstAdministratorBootstrap {

    /** Audit action for the most privileged event in a deployment's life. */
    public static final String ADMINISTRATOR_CREATED = "BOOTSTRAP_ADMINISTRATOR_CREATED";

    private static final String AUDITED_ENTITY = "User";

    private final BootstrapProperties properties;
    private final BootstrapCredentialResolver credentialResolver;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BootstrapCompletionRepository completionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;

    public FirstAdministratorBootstrap(
            BootstrapProperties properties,
            BootstrapCredentialResolver credentialResolver,
            UserRepository userRepository,
            RoleRepository roleRepository,
            BootstrapCompletionRepository completionRepository,
            PasswordEncoder passwordEncoder,
            AuditRecorder auditRecorder) {
        this.properties = properties;
        this.credentialResolver = credentialResolver;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.completionRepository = completionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
    }

    /**
     * Provisions the administrator when bootstrap is enabled.
     *
     * <p>There is no "create one if none exists" branch anywhere in this class. Whether an
     * administrator currently exists is never consulted: the only question is whether bootstrap
     * has already completed, and the database answers that.
     *
     * @return true when an administrator was created by this call
     */
    @Transactional
    public boolean bootstrapIfEnabled() {
        if (!properties.isEnabled()) {
            return false;
        }
        // Re-checked here, not only at context refresh: the flag can be switched on after
        // @PostConstruct has already run, which would otherwise let a null username reach the
        // insert and surface as an unrecognisable integrity violation.
        properties.validate();

        String password = credentialResolver.resolve();
        Role superAdministrator =
                roleRepository
                        .findByName(RoleName.SUPER_ADMINISTRATOR)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Role '"
                                                        + RoleName.SUPER_ADMINISTRATOR
                                                        + "' is missing; reference data has not"
                                                        + " been seeded."));

        // Claim the singleton before anything else. This is the authoritative constraint, so it
        // must be the one a concurrent instance or a repeat run collides with -- otherwise the
        // collision surfaces on users.username, which cannot be distinguished from an unrelated
        // account holding the configured name.
        BootstrapCompletion completion =
                completionRepository.save(BootstrapCompletion.claimFor(properties.getUsername()));
        completionRepository.flush();

        User administrator =
                new User(
                        properties.getUsername(),
                        passwordEncoder.encode(password),
                        properties.getFirstName(),
                        properties.getLastName());
        administrator.setEmail(properties.getEmail());
        administrator.assignRole(superAdministrator);
        // The operator chose this password, not its holder. It is compromised by construction:
        // it passed through a pipeline, a secret store and at least one human (ADR-013).
        administrator.requirePasswordChange();

        User saved = userRepository.saveAndFlush(administrator);
        completion.linkAdministrator(saved);

        auditRecorder.record(
                AuditEvent.of(
                        // No human principal exists: at this moment the account being created is
                        // the first row in the users table (ADR-016).
                        AuditActor.system(), ADMINISTRATOR_CREATED, AUDITED_ENTITY, saved.getId()));

        return true;
    }
}
