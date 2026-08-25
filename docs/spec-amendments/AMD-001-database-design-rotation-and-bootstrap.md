# AMD-001 — Identity rotation flag and bootstrap control table

**Target document:** POS Database Design & ERD Specification v1.0
**Sections affected:** §6.1 (`users`), §5 (Core Entity Map), §22 (Relationship Summary), §23 (Indexing)
**Status:** **Approved** — 2026-08-25 by product owner
**Raised by:** Phase 1 authentication foundation, Story 1.2 pre-implementation
**Date:** 2026-08-25

---

## 1. Why this amendment is needed

Two approved requirements cannot be implemented against the current schema.

**Requirement 1 — enforced initial-password rotation.** The product owner has directed that the
application must *enforce* rotation of the bootstrap administrator's initial password, rather than
relying on an operational runbook. The `users` table defined in §6.1 has no column expressing
"this account must change its password before it can be used", so there is nothing for the
authentication layer to read. Runbook-only rotation was explicitly rejected.

**Requirement 2 — permanently one-shot bootstrap.** The approved provisioning strategy
([ADR-015](../adr/ADR-015-first-administrator-provisioning.md)) requires that first-administrator
creation can never run twice, and specifically that deleting or deactivating the bootstrap
administrator must not allow a later restart to recreate it. That guarantee needs a durable marker
that outlives the administrator row. No such structure exists.

Neither requirement can be met by a workaround:

- Inferring "bootstrap already ran" from the presence of an administrator is precisely the
  resurrection vector the product owner prohibited (requirement 4).
- Using `audit_logs` as the marker couples a security control to a table that §29 contemplates
  archiving and retention policies for. If audit rows are ever pruned, the guard silently
  evaporates and bootstrap becomes re-runnable.

---

## 2. Proposed change A — `users.is_password_change_required`

Add one column to the `users` table in §6.1:

| Column | Type | Constraints |
|--------|------|-------------|
| `is_password_change_required` | BOOLEAN | NOT NULL, default `false` |

**Semantics.** `true` means the account holds a credential it did not choose — issued at bootstrap
or reset by an administrator — and may not be used for anything until the holder replaces it.
Set to `false` by a successful password change, and never by any other operation.

**Default `false`** so the column is inert for every existing and future account unless something
deliberately sets it. Adding the column changes no current behaviour.

### Naming — resolved

**Approved name: `is_password_change_required`.** The product owner directed that the existing §4
convention stands: boolean columns keep the `is_` prefix. §4 is not amended.

---

## 3. Proposed change B — `bootstrap_completions` control table

Add a control table recording that first-administrator provisioning has completed.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| `id` | UUID | PK | Identifier |
| `is_singleton` | BOOLEAN | NOT NULL, UNIQUE, CHECK (`is_singleton` IS TRUE) | Structurally permits at most one row |
| `completed_at` | TIMESTAMPTZ | NOT NULL | When bootstrap succeeded |
| `administrator_user_id` | UUID | FK `users(id)`, NULL, ON DELETE SET NULL | The account created |
| `administrator_username` | VARCHAR(100) | NOT NULL | Immutable textual record, survives account deletion |

### Why the singleton constraint carries the security property

The one-shot guarantee is enforced by the database, not by application logic:

- **Permanently one-shot** (requirement 3): `UNIQUE (is_singleton)` combined with the CHECK means a
  second insert is rejected by PostgreSQL. No code path can bypass it.
- **No resurrection** (requirement 4): the row survives deletion of the administrator, because
  `ON DELETE SET NULL` releases the foreign key rather than cascading. `administrator_username`
  preserves the forensic record after the account is gone.
- **Concurrency-safe** (requirements 8 and 9): when two instances start together, both attempt the
  insert and the database arbitrates. The loser receives a constraint violation, which it treats as
  "another instance completed bootstrap" and continues starting normally rather than crash-looping.

Expressing these as a constraint rather than as application checks means the guarantee holds even
if the bootstrap code is later refactored, and it removes the need for advisory locks or a
distributed lock in Redis.

### Naming — resolved

**Approved name: `bootstrap_completions`.** The product owner directed that §4's plural-table
convention stands. The table holds at most one row, but it is named as the set of completed
bootstraps so no convention exception is needed.

### Placement in the document

`bootstrap_completions` is operational control data, not a business domain entity. It is proposed for
§5's **Control** domain alongside `audit_logs` and `notifications`, not for Identity.

---

## 4. What this amendment does NOT change

- No existing column is altered, renamed, retyped, or dropped.
- No existing constraint or relationship is weakened. §22's cardinality summary is unaffected; the
  new foreign key is nullable and optional.
- No index from §23 changes. Neither addition needs a new index: `users` gains a boolean read only
  alongside an already-indexed row, and `bootstrap_completions` holds at most one row.
- `V1__init_schema.sql` is not modified. Both changes ship as a new forward migration, per §26.
- No approved requirement is removed or reinterpreted.

---

## 5. Impact analysis

### 5.1 Database schema

One new migration adds the column and the table. Additive and backward compatible: an older
application build continues to run against the newer schema, because the column defaults and the
table is unreferenced. `ddl-auto: validate` will reject any entity that disagrees, so the
migration and the `User` entity must land together.

### 5.2 User entity

`com.pos.users.domain.User` gains a mapped boolean and the narrow behaviour around it — a query for
whether rotation is outstanding, and a state transition that clears it. The flag must not be
settable from a request payload; it is changed by the password-change flow and by bootstrap, never
by a generic update.

### 5.3 Authentication flow

Authentication itself is unchanged: credentials are verified exactly as before, and a pending
rotation does **not** make login fail. Failing login would leave the holder with no way to fix
their own account.

Instead, login succeeds and the constraint is applied *after* authentication, at the authorization
layer. The distinction matters: rotation is not an authentication outcome, it is an authorization
state, and treating it as such keeps the two concerns separate.

### 5.4 Password-change flow

This is the gap that makes rotation currently unimplementable, and it is why
[AMD-002](AMD-002-rest-api-self-service-password-change.md) exists. There is no approved endpoint
by which an authenticated user changes their own password. Enforcing rotation without providing a
way to satisfy it would lock the first administrator out of the system permanently.

**AMD-001 and AMD-002 must be approved together.** Approving rotation alone produces an
unusable system.

### 5.5 API behaviour

Covered in detail by AMD-002. In summary: one new endpoint, one new error code, and one additive
field on the login response.

### 5.6 Tests

- Migration tests: column exists with the correct type and default; the singleton constraint
  rejects a second row; `ON DELETE SET NULL` preserves the marker when the administrator is deleted.
- Entity tests: default is `false`; the flag is not settable through a generic update path.
- Concurrency test: two simultaneous bootstrap attempts produce exactly one row, and the loser
  does not throw out of startup.
- Security tests: an account with rotation pending is refused on protected endpoints and permitted
  on the password-change endpoint.
- Regression: the existing suite must stay green. `ddl-auto: validate` means an entity/migration
  mismatch fails every context-loading test rather than one.

---

## 6. Risks of approving

- **Two more columns of surface area on the most security-sensitive table.** Mitigated by both
  being inert unless deliberately set.
- **A singleton table is an unusual shape** and may look like over-engineering to a reader who has
  not seen the resurrection scenario. Mitigated by ADR-015 recording the reasoning.

## 7. Risks of not approving

- Requirement 11 (enforced rotation) cannot be delivered; rotation falls back to the runbook the
  product owner rejected.
- Requirements 3, 4, 8 and 9 would have to be enforced in application code inferring state, which
  is exactly the resurrection vector the pre-mortem identified.

---

## 8. Approval record

Approved by the product owner on 2026-08-25, with these resolutions:

1. Change A approved — column `users.is_password_change_required`.
2. Change B approved — table `bootstrap_completions`.
3. Existing §4 naming conventions stand; §4 is not amended.
4. Approved jointly with AMD-002, as required.

**Implementation may proceed.** The column and table land in Story 1.2, not Story 1.3.
