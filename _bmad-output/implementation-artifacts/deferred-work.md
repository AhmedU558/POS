# Deferred Work

Findings that are real but do not belong to the story that surfaced them.
Append-only. Do not edit existing entries.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: Disable `spring.jpa.open-in-view`, which defaults to true and lets lazy associations resolve outside a transaction.
  evidence: Story 1.1 introduced the first lazy associations. With OSIV on, a web request masks N+1 and makes the fetch-join queries optional, so nothing forces callers onto them. The setting lives in application.yml, which spec 1.1 fenced as Ask First. Belongs with the first story that serves an authenticated web request (1.4/1.5).

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: No optimistic locking on `users`; concurrent deactivation and role edits are last-write-wins.
  evidence: `User` has no `@Version` and V1 has no version column. Adding one changes the `users` table, which Database Design section 6.1 defines without it — a specification amendment, not a developer decision. Matters because AUTH-005 deactivation is a security action.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: No Bean Validation on identity entities; blank or oversized values fail at the database rather than at the boundary.
  evidence: Entities carry only JPA length/nullable metadata. API spec section 35 puts Bean Validation on request DTOs, which do not exist yet. Constructor-level domain validation should land with Story 1.2, which is the first code that actually creates a user.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: No `findByUsernameOrEmail` or `findByIdWithRolesAndPermissions` lookup.
  evidence: UI/UX section 9.1 offers a combined username/email login field, and an authenticated request identifies its subject by id rather than username. Both were left out deliberately: adding untested repository methods ahead of a caller is what the review flagged elsewhere in this story. Add them with the login story.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: Username and email lookups are case-sensitive with no normalization strategy.
  evidence: `users.email` is a plain VARCHAR UNIQUE, so `Foo@x.com` and `foo@x.com` are distinct accounts and a login lookup misses on case mismatch. Needs a decision between citext, a functional unique index on lower(email), or normalizing on write — all of which touch the approved schema.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: Nothing guards `ddl-auto: validate` in the main application configuration.
  evidence: `schemaValidationIsActiveSoEntityDriftCannotPassSilently` reads the value under the test profile, so it pins the suite's own conformance check rather than the runtime setting. Relaxing main application.yml to `none` would lose schema validation in production with no test turning red.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: No shared serializer for the audit before/after payloads, so each module will invent its own JSON shape.
  evidence: `AuditEvent` takes raw JSON strings. Field naming, date formats and null handling will diverge across modules in a table that can never be corrected afterwards. Belongs with the first module that emits a value diff.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: No redaction policy for sensitive data written into audit value payloads.
  evidence: Nothing stops a module writing a password hash, card number or PII into an append-only, undeletable table. Needs a policy decision (allow-list, deny-list, or a masking hook) before modules start emitting entity snapshots.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: Database Design §28's "restricted modification rights" is enforced only by trigger; no GRANT/REVOKE exists.
  evidence: The application connects as the table owner, so it could disable the trigger. A separate least-privilege database role is infrastructure work belonging to Phase 11/12 hardening, not to this story.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: No tamper-evidence to complement the preventive trigger.
  evidence: The immutability guarantee rests on a trigger a table owner can disable — which the migration explicitly anticipates for retention jobs. There is no hash chain, sequence check, or off-box shipping that would let anyone detect out-of-band modification after the fact. Worth an explicit decision.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: `findByEntityTypeAndEntityIdOrderByCreatedAtDesc` returns an unbounded List.
  evidence: A long-lived, frequently modified entity loads its entire history into memory. This is the query the REST API §25 read endpoints will build on, so a Pageable variant belongs with that story.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: No index or partitioning strategy supports time-range or retention queries over audit_logs.
  evidence: Both §23 indexes lead with entity or actor, so neither serves `WHERE created_at < ?` across the whole table. Needs an approved retention policy first — Database Design §29 raises retention but sets none.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: Nothing populates AuditRequestContext outside tests — no filter or interceptor captures IP, user agent, or the current principal.
  evidence: Every caller must hand-thread all three and will pass `none()` by default. The plumbing belongs with Story 1.5, which introduces the security context an interceptor would read.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: Audit actions have no shared vocabulary, so the same logical action can persist under several spellings.
  evidence: `action` is a validated, trimmed string by design (a central catalogue would couple every module to the audit module), but nothing prevents `PRICE_UPDATED` and `PRODUCT_PRICE_UPDATED` describing the same event. A naming convention should be agreed before many modules emit.

- source_spec: `spec-1-3-audit-foundation.md`
  summary: Non-transactional audit tests commit rows that immutability makes impossible to clean up.
  evidence: `AuditSchemaTests` and `AuditTransactionTests` leave probe rows in the shared container permanently, and `systemActionsAreRecordedWithNoActorAndNoUsersInTheDatabase` empties `users`, which will fail the first time any test commits a human-actor audit row. Needs a per-class database isolation strategy rather than a cleanup.

- source_spec: none
  summary: Rotation enforcement and self-service password change — POST /auth/change-password, the PASSWORD_CHANGE_REQUIRED error code, the enforcement filter, and tightening SecurityConfig so only login/forgot-password/reset-password stay public.
  evidence: Split from Story 1.2 at the workflow's multi-goal checkpoint. Independently shippable, and tested with mock principals rather than container startup. MUST land before Story 1.4 — the is_password_change_required flag Story 1.2 sets is inert until this enforces it, so shipping login first would let the bootstrap administrator authenticate without rotating, which is the hole rotation exists to close. AMD-002 is already approved and specifies the whole surface.

- source_spec: `spec-1-2-first-administrator-provisioning.md`
  summary: The resolved bootstrap credential lives as a String in a long-lived singleton and is never cleared.
  evidence: BootstrapProperties.password, the resolver return value and Files.readString all produce Strings that stay reachable in the heap for the process lifetime. Actuator exposes only /health so /actuator/env is closed, but a heap dump would still contain it. Clearing shared configuration state from a service is surprising enough to warrant a deliberate decision rather than a quiet patch.

- source_spec: `spec-1-2-first-administrator-provisioning.md`
  summary: Secret file permissions are not checked; a world-readable mount is accepted silently.
  evidence: BootstrapCredentialResolver validates the file is regular, small, single-line and long enough, but not that it is unreadable by other users. A POSIX permission check would warn on a 0644 secret mount.

- source_spec: `spec-1-2-first-administrator-provisioning.md`
  summary: BootstrapRunner is an ApplicationRunner, so execution-time failures occur after the web server is already accepting connections.
  evidence: Configuration fail-closed is covered — BootstrapProperties.validate() is @PostConstruct and aborts context refresh before the port opens. But credential resolution and provisioning run in the ApplicationRunner callback, after the server starts. Moving them earlier is a lifecycle change worth deciding deliberately.

- source_spec: `spec-1-2-first-administrator-provisioning.md`
  summary: Failed bootstrap attempts produce no audit record.
  evidence: Only success is audited. A failure -- missing role, duplicate username, unreadable secret, losing the race -- leaves nothing in audit_logs, so repeated failed attempts have no forensic trail. Auditing a failure needs a record that survives the rollback, which contradicts the MANDATORY propagation Story 1.3 chose deliberately; resolving that is a design decision, not a patch.

- source_spec: `spec-1-2-first-administrator-provisioning.md`
  summary: V4 takes an ACCESS EXCLUSIVE lock on users with no lock_timeout.
  evidence: ALTER TABLE users ADD COLUMN is fast in PostgreSQL 11+ because the default is not volatile, but the statement still queues behind long-running transactions and blocks all reads while waiting. A lock_timeout belongs in the deployment runbook alongside the migration policy.

- source_spec: `spec-1-2-first-administrator-provisioning.md`
  summary: BCrypt silently truncates input beyond 72 bytes.
  evidence: A passphrase longer than 72 bytes contributes no entropy past that point. Verification truncates identically so nothing breaks, but an operator supplying a long passphrase gets less strength than they believe. Worth a documented note or an explicit guard when the password policy is next revisited.
