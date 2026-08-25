---
title: 'Story 1.1 — Identity & RBAC persistence foundation'
type: 'feature'
created: '2026-08-25'
status: 'done'
baseline_commit: 'e852095112827b4c0a78d260002552807a1052b7'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** V1 created the five identity tables, but nothing can read or write them — no entities, no repositories, no reference data. Every later Phase 1 story must load a user and resolve that user's permissions, so all of them are blocked on this.

**Approach:** Map the existing tables with JPA entities and repositories, and add a `V2` migration seeding the approved roles and the identity permission codes. Persistence and reference data only: no endpoint, no token, no credential.

## Boundaries & Constraints

**Always:**
- Entities map onto V1 exactly. `V1__init_schema.sql` is never edited.
- Seed data is idempotent — re-running the migration must not duplicate rows.
- Least privilege: a role holds only permissions an approved document grants it.
- `passwordHash` never appears in `toString`, logs, or any exposed representation.
- Deleting a user must not delete `Role` or `Permission` rows.
- All 35 existing backend tests stay green.

**Ask First:**
- Granting any identity permission to a role other than Super Administrator.
- Seeding permission codes for modules that do not exist yet.
- Any change to `SecurityConfig`, `application.yml`, or V1.

**Never:**
- API endpoints, DTOs, controllers, or services beyond repositories.
- JWT, login, refresh tokens, authentication filter, `UserDetailsService`.
- Creating user rows or password hashes as seed/production data (Story 1.2).
- Frontend changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fresh migrate | Empty database | 6 roles, 5 permissions, 5 grants — all to Super Administrator | N/A |
| Re-run seed | V2 already applied | Row counts unchanged; no duplicates | `ON CONFLICT DO NOTHING` |
| Entity/schema agreement | Context starts, `ddl-auto: validate` | Context starts | Hibernate fails startup on any mismatch |
| Resolve permissions | User holding Super Administrator | All 5 identity permissions resolve through the role | N/A |
| Least privilege | Cashier, Store Manager, and the other non-admin roles | Zero identity permissions each | N/A |
| Secret exposure | `toString()` on a populated `User` | Hash absent from output | N/A |
| Join-table cleanup | Delete a user holding roles | `user_roles` rows go; `roles` rows survive | No cascade to `Role` |
| Unknown role lookup | Query a name that is not seeded | Empty result | No exception |

</frozen-after-approval>

## Code Map

- `db/migration/V1__init_schema.sql` — **read-only.** The column contract entities must match: `users(id, username, email, password_hash, first_name, last_name, is_active, last_login_at, created_at, updated_at)`, `roles(id, name, description)`, `permissions(id, code, description)`, composite-PK join tables. No `DEFAULT` on any `id` — ids are application- or seed-generated.
- `db/migration/` — V2 goes here (ADR-008); next free version is `V2`.
- `com/pos/users/` — target package; on disk but **empty**. Sub-packages `domain`, `repository` (API §35).
- `test/java/com/pos/AbstractIntegrationTest.java` — extend for the PostgreSQL container.
- `test/java/com/pos/common/FlywayMigrationTests.java:29` — regression surface: history row 0 must stay version `1`. `:88`/`:96` insert users via JDBC and must keep working.
- `specs/spec-pos-system/roles-and-permissions.md` — the six role names verbatim; the seed source.

`gen_random_uuid()` is core PostgreSQL from 13 and the project pins `postgres:15-alpine`, so no `pgcrypto` extension is needed.

## Tasks & Acceptance

**Execution:**
- [x] `db/migration/V2__seed_identity_reference_data.sql` — seed 6 roles and 5 identity permissions, grant all 5 to Super Administrator by sub-select; every statement idempotent.
- [x] `com/pos/users/domain/Permission.java` — entity on `permissions`; leaf, no outbound associations.
- [x] `com/pos/users/domain/Role.java` — entity on `roles`, `@ManyToMany` to `Permission` over `role_permissions`; owning side of the grant edge.
- [x] `com/pos/users/domain/User.java` — entity on `users`, `@ManyToMany` to `Role` over `user_roles`, Hibernate-managed timestamps, `toString` excluding the hash — also closes the missing `updated_at` trigger from the takeover audit.
- [x] `com/pos/users/repository/{UserRepository,RoleRepository,PermissionRepository}.java` — Spring Data interfaces keyed on the unique business columns.
- [x] `test/java/com/pos/users/IdentitySeedDataTests.java` — seeded rows, grant edges, least privilege, idempotency.
- [x] `test/java/com/pos/users/IdentityPersistenceTests.java` — entity graph, timestamps, hash non-exposure, join-table cleanup.

**Acceptance Criteria:**
- Given a fresh database, when migrations run, then exactly the six approved role names exist and no seeded role other than Super Administrator holds an identity permission.
- Given the entities exist, when any Spring context starts under `ddl-auto: validate`, then startup succeeds — proving entity and migration agree.
- Given a persisted user, when it is reloaded, then `createdAt` and `updatedAt` are populated without a database trigger.
- Given the existing suite, when `mvn -B clean verify` runs, then all 35 prior tests still pass.

## Spec Change Log

- **Review iteration 1 (step-04, three subagent reviewers).** No loopback: no finding was an
  intent_gap or bad_spec. The spec stated clearly what to verify; the first implementation wrote
  tests that appeared to verify it but did not. 14 findings applied as patches, 6 deferred, 5
  rejected. The three high-severity findings were all verification gaps proven by mutation
  testing: removing `ON CONFLICT (code)` from V2, swapping `@UpdateTimestamp` for
  `@CreationTimestamp`, and deleting the `JOIN FETCH` clauses each left the suite green. Each now
  fails the test written to catch it, re-verified by mutating and reverting.
  **KEEP on any future re-derivation:** the exhaustive `seedsExactlyTheIdentityPermissionCodes`
  assertion (it is an intentional guard against a module seeding codes ahead of its endpoints),
  the Super-Administrator-only grant, and the non-transactional detached-read test — a
  transactional test cannot distinguish a fetch join from a lazy one.

## Design Notes

**Where roles live.** SAD §4.1 lists `roles` as its own module, but §6 assigns "users, roles, permissions" to one *Auth & Users* responsibility, §27's package structure omits `roles`, and API §7 groups the three resources in one endpoint table. Three of four favour one module, so `Role` and `Permission` live in `com.pos.users.domain`. The empty `com/pos/roles/` directory is untracked scaffolding.

**Open documentation gap — do not resolve here.** UI/UX §33 marks *Users/Roles* as "Limited" for Store Manager, but no approved document says what "Limited" grants. This story seeds identity permissions to Super Administrator only. The gap is decided when Story 1.8 builds those endpoints.

## Verification

**Commands:**
- `cd backend && mvn -B clean verify` — expected: `BUILD SUCCESS`, 35 prior tests plus the new identity tests, 0 failures.
- Re-running `mvn -B clean verify` — expected: still green; proves seed idempotency across a fresh container each run.
- `git status --porcelain` — expected: no secrets, no build output, `V1__init_schema.sql` unmodified.

## Suggested Review Order

**Reference data — what the system now believes about roles**

- Start here: the approved roles and codes, and why only one role gets a grant.
  [`V2__seed_identity_reference_data.sql:15`](../../backend/src/main/resources/db/migration/V2__seed_identity_reference_data.sql#L15)

- The least-privilege decision and the recorded "Limited" documentation gap.
  [`V2__seed_identity_reference_data.sql:41`](../../backend/src/main/resources/db/migration/V2__seed_identity_reference_data.sql#L41)

- Codes as constants so an authorization typo fails to compile, not at runtime.
  [`PermissionCode.java:24`](../../backend/src/main/java/com/pos/users/domain/PermissionCode.java#L24)

**Entity mapping — the parts most likely to be subtly wrong**

- Password hash shielded from serialization as well as from toString.
  [`User.java:120`](../../backend/src/main/java/com/pos/users/domain/User.java#L120)

- permissionCodes deliberately ignores isActive; the javadoc says so loudly.
  [`User.java:173`](../../backend/src/main/java/com/pos/users/domain/User.java#L173)

- equals compares via getter, so a Hibernate proxy resolves before comparison.
  [`Permission.java:64`](../../backend/src/main/java/com/pos/users/domain/Permission.java#L64)

- No cascade: revoking a grant must never delete shared reference data.
  [`Role.java:44`](../../backend/src/main/java/com/pos/users/domain/Role.java#L44)

- One query for the whole authorization graph, instead of one per role.
  [`UserRepository.java:29`](../../backend/src/main/java/com/pos/users/repository/UserRepository.java#L29)

**Tests that survive mutation — each was proven to fail when its mechanism is removed**

- Executes the real migration file; a hand-copied replica would not have caught this.
  [`IdentitySeedDataTests.java:85`](../../backend/src/test/java/com/pos/users/IdentitySeedDataTests.java#L85)

- Compares against the prior updatedAt, not createdAt, so a stale column fails.
  [`IdentityPersistenceTests.java:108`](../../backend/src/test/java/com/pos/users/IdentityPersistenceTests.java#L108)

- Reads outside a transaction: the only place a fetch join differs from lazy.
  [`IdentityDetachedGraphTests.java:50`](../../backend/src/test/java/com/pos/users/IdentityDetachedGraphTests.java#L50)

- Fast domain tests needing neither Spring nor Docker.
  [`IdentityDomainTests.java:22`](../../backend/src/test/java/com/pos/users/IdentityDomainTests.java#L22)
