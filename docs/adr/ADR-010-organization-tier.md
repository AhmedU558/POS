# ADR-010: `stores` is the root organisational entity

**Status:** Accepted — Phase 0
**Date:** 2026-08-25

## Context

The takeover brief referred to "organization/store/register relationships", which implies a tenancy
tier above `stores`. The Phase-0 audit flagged this as a possible gap, because no approved document
defines such an entity. All seven approved documents were re-read specifically for this question.

### What each document actually says

| Document | Every mention of "organization" | Defines an organization entity? |
|---|---|---|
| **PRD** | No occurrence. | No |
| **SRS** | Section 3, role table only: Super Administrator has "Full system, organization, stores, users, roles, configuration, and reports." Used as a scope adjective. Section 10.1 "Core Entities" begins at *Stores, Terminals, Register Sessions*. | No |
| **SAD** | Section 13, primary data domains: "Organization → Stores / Terminals / Registers". A **domain grouping label**, not a table. Section 4.1 module list contains `stores` and `terminals`; no `organizations` module. | No |
| **Database Design & ERD** | Section 5 core entity map, domain column: "Organization \| stores, terminals, registers, register_sessions". Section 7 is titled "Organization, Stores & Terminals" and its table list is exactly `stores`, `terminals`, `registers`, `register_sessions`. Section 21's ERD is rooted at `STORES`. Section 22's cardinality summary has no parent above `stores`. | No |
| **REST API Specification** | No occurrence. Section 8 is "Stores, Terminals & Registers APIs"; there is no `/organizations` resource. Section 30 scopes access by *store*: "Every store-scoped resource must be checked against the authenticated user's permitted stores." | No |
| **Roles & Permissions (SPEC companion)** | No occurrence. Roles are Super Administrator, Store Manager, Cashier, Inventory Manager, Accountant, Online Order Staff — all store-relative. | No |
| **Implementation Plan** | Section 11 and the phase table title Phase 1 "Authentication, RBAC & **Organization**", whose scope bullets are: users, roles, permissions, login/refresh/logout, **store access scope**, stores, terminals, registers, register session foundation. | No |

### Finding

The word "organization" is used consistently across all seven documents as a **domain-grouping
label** for the stores/terminals/registers cluster. Not one document defines an organization table,
entity, endpoint, permission, or relationship. The specifications are internally consistent with
each other; the apparent gap came from the brief's phrasing, not from the documentation.

## Classification

This is **a documentation/terminology inconsistency between the takeover brief and the approved
specifications**, and nothing more. Specifically it is:

- **Not an implementation defect.** The migration models exactly what the Database specification
  defines for the identity domain, and defines nothing the specification does not.
- **Not an incomplete Phase-0 foundation.** Phase 0 owns identity tables only (Database
  specification section 31 checklist). `stores`, `terminals`, `registers` and `register_sessions`
  belong to Phase 1 (Implementation Plan section 11).
- **Not an optional concern**, because leaving it unrecorded invites a future story to invent an
  `organizations` table that no requirement asks for, which would then have to be threaded through
  every store-scoped query and permission check.

It **is** a prerequisite clarification for Story 0.1 and Phase 1, since the store-scope rule in
REST API Specification section 30 is written entirely in terms of stores.

## Decision

`stores` is the root organisational entity for this system. No `organizations` table will be
created.

Multi-store operation is expressed exactly as the approved documents describe it:

```
stores 1 ── N terminals
stores 1 ── N registers
terminals 1 ── N registers
registers 1 ── N register_sessions
users 1 ── N register_sessions
```

Tenancy and data scoping are enforced through the **user-to-store access scope** named in
Implementation Plan section 11 and required by REST API Specification section 30 — that is, a
user-to-store association, not a parent organisation row.

If a genuine multi-tenant requirement appears later (separate businesses sharing one deployment),
it is a change-controlled amendment to the PRD and Database specification, not a developer
decision.

## Consequences

- **No schema change in Phase 0.** No foundation correction is required, because the foundation
  does not contradict the approved model.
- Story 0.1 and Phase 1 design the user-to-store association as the scoping mechanism.
- Reviewers who expect an organisation tier are directed here rather than adding one.
