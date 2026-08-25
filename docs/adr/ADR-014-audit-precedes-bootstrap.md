# ADR-014: The audit foundation is built before first-administrator provisioning

**Status:** Accepted — Phase 1
**Date:** 2026-08-25

## Context

The derived Phase 1 order placed first-administrator provisioning (Story 1.2) before the audit
foundation (Story 1.3). Reviewing the provisioning design exposed the conflict: creating the first
administrator is the single most privileged event in a deployment's life — it manufactures an
account holding every identity permission, from a credential no user chose — and under the original
order it would have been the one privileged event that left no audit record.

The approved requirements do not allow that:

- **AUTH-007** — significant authentication events are recorded in audit logs (MUST).
- **AUD-001** — security-sensitive actions are logged (MUST).
- **AUD-002** — records carry actor, action, target and timestamp (MUST).
- **PRD §21** — "User and permission changes" are named explicitly as auditable.

Building provisioning first would have meant either shipping a privileged action with no audit
trail and backfilling later, or writing the audit call against a table that does not yet exist.
Backfilled auditing is worse than it sounds: the gap is precisely at first deployment, when
forensics would most want to know whether bootstrap ran once or repeatedly.

## Decision

Story 1.3 (audit foundation) is implemented before Story 1.2 (first-administrator provisioning).
Bootstrap is auditable from its first ever execution rather than from some later release.

Revised Phase 1 order:

| # | Story | State |
|---|-------|-------|
| 1.1 | Identity & RBAC persistence foundation | Complete (`e278016`) |
| **1.3** | **Audit foundation** | **Next** |
| **1.2** | **First-administrator provisioning** | After audit |
| 1.4 | Login and JWT access token | |
| 1.5 | JWT filter, security context, `/auth/me` | |
| 1.6 | Refresh token, revocation, logout | |
| 1.7 | Permission enforcement | |
| 1.8 | User and role administration APIs | |
| 1.9 | Stores, terminals, registers, store scope | |
| 1.10 | Login and forced-rotation UI | |

Story numbers are kept rather than renumbered, so that references in the commit history, the
takeover audit and prior discussion stay valid. **The number is an identifier, not a position.**

## Consequences

- Story 1.3 acquires a dependency it did not have: it must settle the **SYSTEM actor convention**
  before Story 1.2 can emit its bootstrap event.
- Nothing else in Phase 1 reorders. Audit had no dependency on provisioning; the edge only ran the
  other way, which is why the original order was wrong rather than merely suboptimal.
- Story 1.2's definition of done gains an audit assertion.

## The dependency Story 1.3 must resolve

Database Design §20.1 defines `audit_logs.actor_user_id` as a foreign key to `users(id)`. A
system-initiated action has no human principal, and at bootstrap time there is not yet a single
user row in the database — so the first audit record the system ever writes is the one that cannot
name an actor.

Two candidate conventions, to be decided by Story 1.3:

1. **`actor_user_id IS NULL` means system-initiated.** No schema change; §20.1 does not mark the
   column NOT NULL. Costs a documented convention that every reader and query must know, and sits
   awkwardly against AUD-002's requirement that records carry an actor.
2. **A reserved SYSTEM user row.** Gives AUD-002 a literal actor, but puts a non-human row in
   `users` that needs a `password_hash` it can never use, and which then has to be excluded from
   every user listing, count and administrative screen.

Neither is free. This ADR does not choose between them — that is Story 1.3's decision, made with
the audit schema in front of it. It is recorded here because Story 1.2 cannot start until it is
settled.
