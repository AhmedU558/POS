---
title: 'Story 4.3 — Supplier Profiles'
type: 'feature'
created: '2026-08-31'
status: 'in-review'
baseline_commit: '2cbbfda'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-023-story-4-3-supplier-profiles.md'
  - '{project-root}/docs/spec-amendments/AMD-015-rest-api-supplier-profiles.md'
  - '{project-root}/docs/spec-amendments/AMD-016-ui-ux-supplier-profiles.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** SUP-001 requires supplier profiles. There is no `suppliers`
table, no `SUPPLIER_*` permissions, and no SCR-018.

**Approach:** Persist `suppliers` (ADR-023 D2), expose the four CRUD
endpoints under permission-only authz (D1), seed Inventory/Accountant/
Manager/Admin grants (D5), audit writes (D6), and ship SCR-018 only.

Binding addenda: ADR-023, AMD-015, AMD-016.

## Boundaries & Constraints

**Always:**
- Columns only as ADR-023 D2. No `store_id`. No AP/balance columns.
- `SUPPLIER_READ` / `SUPPLIER_WRITE` only. No `StoreScopeEvaluator`.
- Duplicate `supplierCode` → 409 `CONFLICT`. No `DELETE`.
- DTOs at the controller; `ApiResponse` / `ApiException` + documented `ErrorCode`.

**Never:**
- `supplier_products`, POs, receipts, invoices, AP, statements.
- Editing an already-applied migration.
- A new `ErrorCode`.

</frozen-after-approval>
