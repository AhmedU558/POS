---
title: 'Story 4.1 — Customer Profiles'
type: 'feature'
created: '2026-08-31'
status: 'in-review'
baseline_commit: 'f7770c5'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-021-story-4-1-customer-profiles.md'
  - '{project-root}/docs/spec-amendments/AMD-011-rest-api-customer-profiles.md'
  - '{project-root}/docs/spec-amendments/AMD-012-ui-ux-customer-profiles.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** CUS-001 requires customer profiles. There is no `customers` table,
no `CUSTOMER_*` permissions, and no SCR-015/SCR-016.

**Approach:** Persist `customers` (ADR-021 D2), expose the four CRUD endpoints
under permission-only authz (D1), seed Cashier/Manager/Admin grants (D7), audit
writes (D8), and ship SCR-015/SCR-016 only.

Binding addenda: ADR-021, AMD-011, AMD-012.

## Boundaries & Constraints

**Always:**
- Columns only as ADR-021 D2. No `store_id`. No `customer_type`.
- `CUSTOMER_READ` / `CUSTOMER_WRITE` only. No `StoreScopeEvaluator`.
- Duplicate `customerCode` → 409 `CONFLICT`. No `DELETE`.
- `creditLimit` is a profile field (`NUMERIC` / `BigDecimal` ≥ 0), not a ledger.
- DTOs at the controller; `ApiResponse` / `ApiException` + documented `ErrorCode`.

**Never:**
- Credit tables/APIs/SCR-017, statements, sales history, suppliers, POS create,
  CUS-003 pricing.
- Editing an already-applied migration.
- A new `ErrorCode`.

</frozen-after-approval>
