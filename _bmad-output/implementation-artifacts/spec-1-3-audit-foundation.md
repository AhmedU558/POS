---
title: 'Story 1.3 — Audit foundation'
type: 'feature'
created: '2026-08-25'
status: 'done'
baseline_commit: 'e27801641c633aa6db20b2eae6b10ac95c782c33'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-014-audit-precedes-bootstrap.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** AUD-001 and AUTH-007 require privileged and authentication events to be audited.
Neither is possible — there is no `audit_logs` table and no way to record anything. ADR-014 moved
this story ahead of provisioning so bootstrap is auditable the first time it runs, not backfilled.

**Approach:** Create the table exactly as Database Design §20.1 specifies, map it as an immutable
entity, and expose one recorder other modules call. Write side only: the API §25 read endpoints
need authentication and permissions that do not exist yet.

## Boundaries & Constraints

**Always:**
- Schema matches §20.1 column for column, plus the two §23 indexes.
- A SYSTEM actor is representable with no authenticated human and no user rows in the database.
- The recorder joins the caller's transaction: a failed audit write rolls back the audited action.
- Audit records are immutable, enforced by the database rather than by convention.
- The repository exposes no way to delete an audit record.
- All 71 existing tests stay green, including `seedsExactlyTheIdentityPermissionCodes`.

**Ask First:**
- Any column beyond §20.1, including an `actor_type` discriminator.
- Seeding `AUDIT_READ` or any permission code.
- Relaxing immutability to allow deletion for retention.

**Never:**
- Audit endpoints, controllers or DTOs.
- Story 1.2 work: bootstrap logic, `bootstrap_completions`, `is_password_change_required`.
- Authentication, JWT, login filter, `UserDetailsService`. Frontend changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error Handling |
|---|---|---|---|
| Human actor | Real user id | Row written, actor set | N/A |
| SYSTEM actor | System actor | Row written, actor NULL | N/A |
| No users exist | SYSTEM event, empty `users` | Row written | Must not need a user row |
| Unknown actor | Non-existent user id | Rejected | FK violation |
| Structured values | JSON old/new supplied | Round-trips through JSONB | N/A |
| Request context | IP + user agent | Stored, IP as native `inet` | N/A |
| No request context | SYSTEM event | Row written, both NULL | N/A |
| Blank action | Blank action or entityType | Rejected before insert | `IllegalArgumentException` |
| Caller rolls back | Throws after recording | **No audit row persists** | Same transaction |
| Update attempt | `UPDATE audit_logs` | Rejected by database | Trigger raises |
| Delete attempt | `DELETE FROM audit_logs` | Rejected by database | Trigger raises |
| User with history | Delete a user holding audit rows | Rejected; trail pins the user | FK violation |

</frozen-after-approval>

## Code Map

- `db/migration/` — V2 last applied, V3 next. V1 defines `users(id)`, the FK target. Both read-only.
- §20.1 column contract: `id`, `actor_user_id`, `action`, `entity_type`, `entity_id`,
  `old_values` JSONB, `new_values` JSONB, `ip_address` INET, `user_agent` TEXT, `created_at`.
  §23 indexes: `(entity_type, entity_id, created_at)`, `(actor_user_id, created_at)`;
  §4 names them `idx_<table>_<columns>`.
- `com/pos/audit/` — empty on disk. Sub-packages `domain`, `repository`, `service` per API §35.
- `AbstractIntegrationTest` supplies the container; `IdentityDetachedGraphTests` is the
  non-transactional pattern.
- `IdentityPersistenceTests.java:136` deletes a user — passes only while that user has no audit
  rows. Regression surface for the new FK.

**Verified, not assumed:** Hibernate 6.4.4 ships `PostgreSQLInetJdbcType` and
`PostgreSQLCastingJsonJdbcType`, so INET and JSONB map via `@JdbcTypeCode` with no new dependency.

## Tasks & Acceptance

**Execution:**
- [x] `db/migration/V3__create_audit_logs.sql` — table per §20.1, both §23 indexes, and a
  `BEFORE UPDATE OR DELETE` trigger that raises — makes AUD-003 structural.
- [x] `audit/domain/AuditActor.java` — sealed `Human` / `SystemProcess` via `AuditActor.user(id)`
  and `AuditActor.system()`; a caller cannot omit the choice.
- [x] `audit/domain/AuditRequestContext.java` — optional IP and user agent.
- [x] `audit/domain/AuditEvent.java` — what to record; rejects blank action or entityType.
- [x] `audit/domain/AuditLog.java` — immutable entity, no setters, JSONB/INET via `@JdbcTypeCode`,
  `actor_user_id` a plain UUID rather than an association.
- [x] `audit/repository/AuditLogRepository.java` — extends `Repository`, **not** `JpaRepository`,
  so no delete method exists to call.
- [x] `audit/service/AuditRecorder.java` — records inside the caller's transaction.
- [x] `docs/adr/ADR-016-system-actor-convention.md` — records the decision ADR-014 left open.
- [x] `test/java/com/pos/audit/AuditSchemaTests.java` — columns, indexes, immutability.
- [x] `test/java/com/pos/audit/AuditRecorderTests.java` — actors, round-trips, validation.
- [x] `test/java/com/pos/audit/AuditTransactionTests.java` — rollback and FK behaviour.

**Acceptance Criteria:**
- Given an empty `users` table, when a SYSTEM event is recorded, a row is written with a NULL
  actor — proving Story 1.2 bootstrap can be audited before any user exists.
- Given an audited operation that fails after recording, when its transaction rolls back, no audit
  row remains.
- Given a persisted audit row, an update or delete attempt is rejected by the database.
- `mvn -B clean verify` keeps all 71 prior tests passing.

## Spec Change Log

- **Review iteration 1 (step-04, three subagent reviewers).** No loopback: no finding was an
  intent_gap or bad_spec — the spec said "immutable, enforced in the database" and "joins the
  caller's transaction", and the implementation under-delivered both. 19 patches applied, 9
  deferred, 1 rejected.
  Two critical findings, both converged on independently: **`TRUNCATE` bypassed the row-level
  trigger entirely**, leaving the whole trail erasable in one statement while two green tests
  reported AUD-003 as enforced; and **`Propagation.REQUIRED` did not deliver its own documented
  guarantee**, since it starts a transaction when none is active and would commit an audit row
  standalone. Two further high-severity findings: unvalidated JSON and unvalidated IP both fail at
  flush and, because audit failures abort the caller by design, would destroy the *business*
  operation — and the IP is attacker-influenced through `X-Forwarded-For`.
  **KEEP on any future re-derivation:** the statement-level TRUNCATE guard, `MANDATORY`
  propagation with its standalone-call test, boundary validation of JSON and IP (discard a bad IP,
  never throw), database-assigned `created_at`, and the exhaustive switch in
  `AuditActor.persistedUserId()`.

## Design Notes

**Why NULL means SYSTEM.** §20.1 does not mark `actor_user_id` NOT NULL. A reserved SYSTEM user row
would need a `password_hash` it can never use, then be excluded from every listing and count.
NULL's ambiguity — system, or did someone forget? — is closed at the application boundary: the
recorder demands an `AuditActor`, so NULL is only reachable via `AuditActor.system()`.

**No action-code catalogue.** Audit actions belong to the modules that emit them; a central
catalogue would couple every module to this one. `action` is a validated string.

**Immutability is enforced twice** — trigger and repository shape. Either alone is bypassable: the
trigger by raw-connection code, the repository by a future author swapping in `JpaRepository`.

## Verification

**Commands:**
- `cd backend && mvn -B clean verify` — `BUILD SUCCESS`, 71 prior plus new audit tests, 0 failures.
- Mutation checks, reverted after each: drop the immutability trigger; set the recorder's
  propagation to `REQUIRES_NEW`. Each must turn its matching test red.
- `git status --porcelain` — no secrets, no build output, V1 and V2 unmodified.

## Suggested Review Order

**The immutability guarantee — start here**

- Both guards. The second exists because TRUNCATE never fires row-level triggers.
  [`V3__create_audit_logs.sql:45`](../../backend/src/main/resources/db/migration/V3__create_audit_logs.sql#L45)

- Named explicitly so the test asserting on it pins a contract, not a Postgres default.
  [`V3__create_audit_logs.sql:12`](../../backend/src/main/resources/db/migration/V3__create_audit_logs.sql#L12)

**The SYSTEM actor decision**

- Sealed, so a null actor is only reachable by asking for it.
  [`AuditActor.java:19`](../../backend/src/main/java/com/pos/audit/domain/AuditActor.java#L19)

- Exhaustive switch: a third actor kind must fail to compile, not silently persist null.
  [`AuditActor.java:47`](../../backend/src/main/java/com/pos/audit/domain/AuditActor.java#L47)

**Where a bad input would destroy the audited operation**

- MANDATORY, not REQUIRED — REQUIRED would commit standalone and hide it.
  [`AuditRecorder.java:41`](../../backend/src/main/java/com/pos/audit/service/AuditRecorder.java#L41)

- Malformed JSON rejected at the boundary; at flush it would roll back the caller.
  [`AuditEvent.java:73`](../../backend/src/main/java/com/pos/audit/domain/AuditEvent.java#L73)

- A spoofed X-Forwarded-For chain is discarded, never thrown.
  [`AuditRequestContext.java:38`](../../backend/src/main/java/com/pos/audit/domain/AuditRequestContext.java#L38)

- Database assigns the time: app clocks skew across scaled instances.
  [`AuditLog.java:77`](../../backend/src/main/java/com/pos/audit/domain/AuditLog.java#L77)

- No delete method exists to call, by choosing Repository over JpaRepository.
  [`AuditLogRepository.java:25`](../../backend/src/main/java/com/pos/audit/repository/AuditLogRepository.java#L25)

**Tests proven by mutation**

- TRUNCATE guard — fails when the statement-level trigger is dropped.
  [`AuditSchemaTests.java:127`](../../backend/src/test/java/com/pos/audit/AuditSchemaTests.java#L127)

- Standalone call refused — fails when propagation reverts to REQUIRED.
  [`AuditTransactionTests.java:80`](../../backend/src/test/java/com/pos/audit/AuditTransactionTests.java#L80)

- Audit shares the caller's fate — fails under REQUIRES_NEW.
  [`AuditTransactionTests.java:57`](../../backend/src/test/java/com/pos/audit/AuditTransactionTests.java#L57)

- The bootstrap condition: a SYSTEM row with the users table empty.
  [`AuditRecorderTests.java:38`](../../backend/src/test/java/com/pos/audit/AuditRecorderTests.java#L38)
