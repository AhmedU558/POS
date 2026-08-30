---
title: 'Story 5.1 — Purchase Order Lifecycle'
type: 'feature'
created: '2026-08-31'
status: 'in-review'
baseline_commit: '2b7fa8c'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-025-story-5-1-purchase-order-lifecycle.md'
  - '{project-root}/docs/spec-amendments/AMD-019-rest-api-purchase-orders.md'
  - '{project-root}/docs/spec-amendments/AMD-020-ui-ux-purchase-orders.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PUR-001 requires purchase-order create/manage. Tables, endpoints,
and SCR-019 were unnamed contracts.

**Approach:** Persist header/lines (D2), six REST endpoints, draft-only
lifecycle (D1), permission seed (D4), audit (D5), SCR-019.

Binding addenda: ADR-025, AMD-019, AMD-020.

## Boundaries & Constraints

**Always:**
- No store scope. No money totals.
- Existing ErrorCodes only. DTOs at the controller.
- Submit/cancel from DRAFT only.

**Never:**
- Goods receipts, inventory updates, invoices, AP, payments, statements.
- Editing an already-applied migration.

</frozen-after-approval>
