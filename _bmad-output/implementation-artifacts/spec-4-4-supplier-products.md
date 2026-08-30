---
title: 'Story 4.4 — Supplier-Product Associations'
type: 'feature'
created: '2026-08-31'
status: 'in-review'
baseline_commit: '78db527'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-024-story-4-4-supplier-products.md'
  - '{project-root}/docs/spec-amendments/AMD-017-rest-api-supplier-products.md'
  - '{project-root}/docs/spec-amendments/AMD-018-ui-ux-supplier-products.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** SUP-002 requires associating products with suppliers. There is no
`supplier_products` table and no GET/PUT `/suppliers/{id}/products`.

**Approach:** Persist the join table (ADR-024 D1), expose replace-set APIs
(D2–D3), audit the PUT (D4), and show the list on SCR-018 (D5).

Binding addenda: ADR-024, AMD-017, AMD-018.

## Boundaries & Constraints

**Always:**
- Join columns only. No supplier SKU/cost/lead-time fields.
- Existing `SUPPLIER_READ` / `SUPPLIER_WRITE`. No `StoreScopeEvaluator`.
- PUT replaces the set. Unknown supplier/product → 404.
- DTOs at the controller; documented `ErrorCode` only.

**Never:**
- Purchasing, POs, receipts, invoices, AP, statements.
- Editing an already-applied migration.
- A new `ErrorCode` or permission code.

</frozen-after-approval>
