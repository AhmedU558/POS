---
title: 'Story 1.2 — First-administrator provisioning'
type: 'feature'
created: '2026-08-25'
status: 'done'
baseline_commit: '1e94620'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-015-first-administrator-provisioning.md'
  - '{project-root}/docs/spec-amendments/AMD-001-database-design-rotation-and-bootstrap.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 1.1 seeded roles but no users, and `POST /users` requires `USER_WRITE`, so
nothing can create the first account. The system has no path to its first authentication.

**Approach:** Create one named administrator at startup from operator-supplied credentials, exactly
once, recorded by a database constraint rather than inferred state. Rotation enforcement and the
change-password endpoint are a separate deliverable (deferred-work.md).

## Boundaries & Constraints

**Always:**
- Bootstrap runs only when explicitly enabled. **Never** because no administrator exists.
- Credentials resolve secret-file first, environment second. No default, ever.
- Enabled without a credential fails closed, before the application serves traffic.
- PostgreSQL arbitrates one-shot and concurrency. No Redis lock, no application lock.
- Administrator and completion marker share one transaction; the marker cannot outlive a failure.
- The created account is named, BCrypt-hashed, and flagged `is_password_change_required`.
- The SYSTEM audit event goes through the existing `AuditRecorder`. No second implementation.
- V1, V2 and V3 are never modified. All 101 existing tests stay green.

**Ask First:**
- Any change that loosens `SecurityConfig`.
- Any column or table beyond AMD-001's approved shapes.
- Granting the bootstrap account anything other than the seeded Super Administrator role.

**Never:**
- An unauthenticated setup endpoint, or any API outside the approved contract.
- JWT, login, refresh tokens, `UserDetailsService` — Stories 1.4/1.5.
- `POST /auth/change-password`, the enforcement filter, or `PASSWORD_CHANGE_REQUIRED` — deferred.
- A hardcoded or committed credential.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error Handling |
|---|---|---|---|
| Enabled, fresh database | Flag on, credentials present | Administrator created, marker written | N/A |
| Disabled | Flag off, credentials present | **Nothing created** | N/A |
| Enabled, no credential | Flag on, no file and no env value | Startup fails before serving | Fail closed |
| Enabled, blank credential | Flag on, empty value | Startup fails | Treated as absent |
| Enabled, no username | Flag on, no name supplied | Startup fails | No defaults exist |
| Secret file present | Both file and env supplied | File wins | N/A |
| Secret file unreadable | Path set, file missing | Startup fails | Never silently falls back |
| Success | Bootstrap completes | Marker row exists, exactly one | N/A |
| Second run | Marker already present | No second administrator | Violation caught, start continues |
| Administrator deleted | Account removed, bootstrap re-run | **Still not recreated** | Marker survives deletion |
| Two instances at once | Concurrent execution | Exactly one administrator, one marker | Loser continues, no crash |
| Failure after marker | Administrator insert fails | **No marker persists** | Same transaction rolls back |
| Credential at rest | Administrator persisted | Plaintext appears nowhere | Hash only |
| Created account | Administrator persisted | Rotation flag true, Super Administrator role | N/A |
| Audit | Bootstrap succeeds | SYSTEM-actor audit row exists | Joins the same transaction |

</frozen-after-approval>

## Code Map

- `db/migration/` — V3 last applied; V4 and V5 next. V1/V2/V3 read-only. AMD-001 defines both
  shapes verbatim, including `is_singleton BOOLEAN NOT NULL UNIQUE CHECK (is_singleton IS TRUE)`
  and `administrator_user_id … ON DELETE SET NULL`.
- `users/domain/User.java` gains the mapped boolean; `RoleName.SUPER_ADMINISTRATOR` (seeded by V2)
  is the role to attach. Existing `UserRepository`/`RoleRepository` lookups suffice.
- `audit/service/AuditRecorder.java` is `Propagation.MANDATORY`, so bootstrap must already hold a
  transaction. `AuditActor.system()` is the only route to a null actor (ADR-016).
- `common/security/SecurityConfig.java` — **must not change in this story.**
- `AbstractIntegrationTest` is the container base; `AuditTransactionTests` shows the
  non-transactional pattern with a `@TestConfiguration` bean.

**Ordering constraint:** a PostgreSQL constraint violation aborts the transaction, so the
violation cannot be caught inside the transactional method. The catch belongs one level out, in
the startup runner.

## Tasks & Acceptance

**Execution:**
- [x] `db/migration/V4__add_password_change_required.sql` — the AMD-001 column, defaulting false.
- [x] `db/migration/V5__create_bootstrap_completions.sql` — the AMD-001 table; the unique singleton
  is the one-shot guarantee.
- [x] `users/domain/User.java` — map the flag; a state transition that clears it, never a setter.
- [x] `bootstrap/domain/BootstrapCompletion.java` + repository — marker entity, no delete method.
- [x] `bootstrap/config/BootstrapProperties.java` — enable flag, named-account fields, both
  credential sources; fails at context refresh when enabled and incomplete.
- [x] `bootstrap/service/BootstrapCredentialResolver.java` — file first, env second, no default.
- [x] `bootstrap/service/FirstAdministratorBootstrap.java` — `@Transactional`; account, marker and
  SYSTEM audit event; lets violations propagate.
- [x] `bootstrap/BootstrapRunner.java` — `ApplicationRunner`; catches the violation outside the
  transaction and continues starting.
- [x] `test/java/com/pos/bootstrap/BootstrapSchemaTests.java` — AMD-001 shapes and constraints.
- [x] `test/java/com/pos/bootstrap/BootstrapProvisioningTests.java` — the twelve scenarios.
- [x] `test/java/com/pos/bootstrap/BootstrapConcurrencyTests.java` — real threads, database-level.

**Acceptance Criteria:**
- Given bootstrap has completed, when it runs again with the administrator deleted, then no
  administrator is recreated and startup continues.
- Given two instances start simultaneously, then exactly one administrator and one marker exist and
  neither instance crashes.
- Given bootstrap is enabled with no credential, then startup fails before the application serves.
- Given bootstrap succeeds, then the stored value is a BCrypt hash that verifies the supplied
  password, the account carries the rotation flag and the Super Administrator role, and a SYSTEM
  audit row exists.
- `mvn -B clean verify` keeps all 101 prior tests passing.

## Spec Change Log

- **Review iteration 1 (step-04, three subagent reviewers).** No loopback: nothing was an
  intent_gap or bad_spec. Three critical findings, each demonstrated by the reviewer:
  (1) the **startup trigger was never tested** — every test called `runBootstrap()` directly, so
  removing `implements ApplicationRunner` left the suite green while no deployment would ever
  provision; (2) **property binding was never exercised** — renaming the `@ConfigurationProperties`
  prefix or removing `@PostConstruct` changed nothing observable; (3) an **empty mounted secret
  file** would have provisioned the super-administrator with `encode("")`.
  Also applied: the approved 12-character policy to the bootstrap credential, column-width bounds
  so an over-long username cannot masquerade as an unrecognisable violation, removal of
  `User.setPasswordHash` (which replaced a credential without clearing the rotation flag),
  cycle-safe cause-chain walking, WARN instead of INFO for a still-mounted credential, scoped test
  cleanup, and `position()` instead of `LIKE` in the plaintext sweep.
  **Found by my own tests during implementation, before review:** the runner originally swallowed
  *every* `DataIntegrityViolationException`, so an unrelated duplicate username was reported as
  "already provisioned"; and the marker was written *after* the account, so a repeat run collided
  on `users_username_key` rather than on the authoritative singleton constraint, crash-looping
  every loser. Both fixed and mutation-verified.
  **KEEP on any future re-derivation:** marker-claimed-first ordering, the runner discriminating
  the singleton constraint by name, `BootstrapStartupTests` (own context, no direct call), and
  `BootstrapConfigurationValidationTests` (ApplicationContextRunner — a started context cannot
  demonstrate that refresh fails).

## Design Notes

**Why a marker and not "no administrator exists".** One deletion — a support error, a restore, an
attacker with a single write — would otherwise make the next restart rebuild an administrator using
the operator's long-since-leaked password. The marker survives the account it created.

**Restart is simulated by re-invoking the runner**, not by rebuilding the Spring context. The
runner is what executes at startup, so calling it twice exercises the same guard a restart would;
a context restart per scenario would add minutes without testing anything more.

## Verification

**Commands:**
- `cd backend && mvn -B clean verify` — `BUILD SUCCESS`, 101 prior plus new, 0 failures.
- Mutation checks, each reverted: drop the enable-flag guard; drop the singleton unique constraint;
  make the marker its own transaction; skip the rotation flag; store the raw password; remove the
  audit call; catch the violation inside the transaction.
- `git status --porcelain` — no secrets, no build output, V1/V2/V3 unmodified.

## Suggested Review Order

**The one-shot guarantee — start here**

- Two constraints carry the whole security property; no application check does.
  [`V5__create_bootstrap_completions.sql:27`](../../backend/src/main/resources/db/migration/V5__create_bootstrap_completions.sql#L27)

- SET NULL, not CASCADE: the marker must outlive the account it created.
  [`V5__create_bootstrap_completions.sql:25`](../../backend/src/main/resources/db/migration/V5__create_bootstrap_completions.sql#L25)

- The marker is claimed before the account exists, so the authoritative constraint fires first.
  [`FirstAdministratorBootstrap.java:98`](../../backend/src/main/java/com/pos/bootstrap/service/FirstAdministratorBootstrap.java#L98)

- Only the singleton constraint means "already provisioned"; anything else is a real failure.
  [`BootstrapRunner.java:62`](../../backend/src/main/java/com/pos/bootstrap/BootstrapRunner.java#L62)

**Failing closed**

- Validation at context refresh, so a misconfigured deployment never serves a request.
  [`BootstrapProperties.java:33`](../../backend/src/main/java/com/pos/bootstrap/config/BootstrapProperties.java#L33)

- Re-checked at execution: the flag can be switched on after the context is up.
  [`FirstAdministratorBootstrap.java:80`](../../backend/src/main/java/com/pos/bootstrap/service/FirstAdministratorBootstrap.java#L80)

- An empty secret mount would otherwise provision the super-administrator with encode("").
  [`BootstrapCredentialResolver.java:88`](../../backend/src/main/java/com/pos/bootstrap/service/BootstrapCredentialResolver.java#L88)

**The account it creates**

- Compromised by construction, so it is created already requiring rotation.
  [`FirstAdministratorBootstrap.java:112`](../../backend/src/main/java/com/pos/bootstrap/service/FirstAdministratorBootstrap.java#L112)

- SYSTEM actor: no human principal exists at this moment (ADR-016).
  [`FirstAdministratorBootstrap.java:117`](../../backend/src/main/java/com/pos/bootstrap/service/FirstAdministratorBootstrap.java#L117)

**Tests proven by mutation**

- The production entry point: nothing is called, the context simply starts.
  [`BootstrapStartupTests.java:50`](../../backend/src/test/java/com/pos/bootstrap/BootstrapStartupTests.java#L50)

- A started context cannot show that refresh fails; this one can.
  [`BootstrapConfigurationValidationTests.java:48`](../../backend/src/test/java/com/pos/bootstrap/BootstrapConfigurationValidationTests.java#L48)

- The resurrection vector, asserted directly.
  [`BootstrapProvisioningTests.java:277`](../../backend/src/test/java/com/pos/bootstrap/BootstrapProvisioningTests.java#L277)

- Four real threads against the real database, not a simulated race.
  [`BootstrapConcurrencyTests.java:68`](../../backend/src/test/java/com/pos/bootstrap/BootstrapConcurrencyTests.java#L68)
