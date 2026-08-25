# ADR-015: The first administrator is provisioned from operator-supplied secrets at startup

**Status:** Accepted — Phase 1. AMD-001 approved 2026-08-25.
**Date:** 2026-08-25
**Related:** [ADR-013](ADR-013-forced-initial-password-rotation.md),
[ADR-014](ADR-014-audit-precedes-bootstrap.md),
[AMD-001](../spec-amendments/AMD-001-database-design-rotation-and-bootstrap.md)

## Context

Story 1.1 seeded six roles and granted all five identity permissions to Super Administrator, but
seeded zero users. `POST /users` requires `USER_WRITE`, so no user can be created through the API
until an administrator already exists. The system has no path to its first authentication.

No approved document specifies how the first administrator comes into being. Searching the PRD,
SRS, SAD, Database Design, REST API Specification, UI/UX Specification, Implementation Plan,
`roles-and-permissions.md` and `business-rules.md` for bootstrap, first-admin, initial-credential,
seed-user, setup-wizard and provisioning terms returns nothing. This is a genuine gap, decided
here rather than invented in code.

Four options were evaluated across security, operational complexity, development complexity,
deployment, testing, auditability, architectural fit and documentation alignment.

## Decision

**Operator-supplied bootstrap at application startup**, hardened against the failure modes the
pre-mortem and security-persona review identified.

### Shape

- Bootstrap runs only when an **explicit enable flag** is set. It never triggers merely because no
  administrator exists — inferring intent from absent state is the resurrection vector below.
- Credentials resolve from a **mounted secret file first**, falling back to an environment
  variable. The `_FILE` suffix convention matches the official Postgres and MySQL images, so
  operators already know it, and Docker and Kubernetes secret mounts work without translation.
  Local development may use the plain variable; production should not.
- The account is **named**. Username, given name and family name have no defaults and must be
  supplied, so the result is an identifiable person rather than a generic `admin`.
- Completion is recorded in the `bootstrap_completions` singleton table (AMD-001), making the operation
  **permanently one-shot at the database level**.
- The created account is marked for **forced password rotation** (ADR-013).
- Completion emits an **audit event** using the SYSTEM actor convention (ADR-014).

### The resurrection vector this is designed against

The first draft guarded bootstrap with "create an administrator if none exists". The pre-mortem
showed that this is a standing re-entry path: delete or deactivate the administrator — through a
support error, a restore from backup, or an attacker who achieves exactly one deletion — and the
next restart **recreates an administrator with the operator's original, long-since-leaked
password**. The guard would helpfully rebuild the account an attacker wanted.

The guard is therefore *"bootstrap has never completed"*, recorded durably and independently of
whether any administrator currently exists. The marker survives deletion of the account it created.

### Concurrency

The Architecture Document requires horizontal scaling, so simultaneous startup is normal rather
than exceptional. Both instances attempt the insert; the unique constraint lets the database pick a
winner. The loser treats the constraint violation as "another instance completed bootstrap" and
continues starting. No advisory lock, no Redis lock, and no crash loop.

## Options rejected

**Seeded credential in a migration.** Puts a bcrypt hash in version control permanently, where a
fork, a leaked repository or a contractor's laptop yields an offline cracking target with unlimited
time. Every deployment would share one initial credential. Contradicts SAD §15 in spirit, and
Database Design §26 treats seed data as reference data — a user account is not reference data.

**One-time unauthenticated setup endpoint.** Introduces an unauthenticated privileged endpoint into
a default-deny filter chain, with a race between deployment and setup in which whoever arrives
first owns the system. Closing that race requires a setup token, which reintroduces the same
secret-distribution problem plus an endpoint. REST API Specification §4.2 enumerates the
authentication endpoints and contains no setup endpoint. Rejected by the product owner
(requirement 13).

**Operator-run one-shot CLI command.** Genuinely strong — no standing secret anywhere, and a human
actor for the audit record. Rejected only because it presumes shell access at deploy time, which no
approved document establishes, and it degrades local-developer ergonomics. **If the deployment
target becomes Kubernetes, this decision should be revisited**, as the trade-off reverses.

## Consequences

- Bootstrap configuration must be removed or rotated after first deployment. ADR-013's forced
  rotation means a leaked bootstrap password is useless once the administrator has logged in once,
  which converts an operational hope into a technical guarantee.
- The environment variable remains readable by anyone with host or container access
  (`docker inspect`, `/proc/<pid>/environ`, crash dumps, APM agents). The secret-file path narrows
  this; forced rotation bounds the damage. It is not eliminated, and that residual risk is accepted
  knowingly.
- Depends on AMD-001. Without the `bootstrap_completions` table, the one-shot guarantee degrades to
  application-inferred state — the rejected design.
- Depends on Story 1.3 having settled the SYSTEM actor convention.
- No new API surface, no unauthenticated endpoint, no committed credential.
