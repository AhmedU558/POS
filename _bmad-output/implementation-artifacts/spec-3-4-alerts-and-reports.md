---
title: 'Story 3.4 — Stock Alerts & Inventory Reports'
type: 'feature'
created: '2026-08-30'
status: 'in-review'
baseline_commit: '80b7f76'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-020-story-3-4-alerts-and-reports.md'
  - '{project-root}/docs/spec-amendments/AMD-009-rest-api-inventory-alerts-reports.md'
  - '{project-root}/docs/spec-amendments/AMD-010-ui-ux-stock-alerts-reports.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** INV-008 / EXP-005 require generated low-stock and expiry alerts
with acknowledge. RPT-003 / RPT-004 require inventory, movement, and
low-stock/expiry reports. None of those APIs or screens exist.

**Approach:** Persist `stock_alerts`, generate from `min_stock` and batch
expiry windows, list/acknowledge under `INVENTORY_READ`, and add the three
`REPORT_INVENTORY` inventory report GETs plus SCR-014 and the Phase 3
inventory slice of SCR-026.

## Boundaries & Constraints

**Always:**
- Columns only as ADR-020 D1. Threshold is `products.min_stock`.
- Alerts: `INVENTORY_READ` + store scope. Reports: `REPORT_INVENTORY` + store scope.
- Generate on adjust/receive and refresh on list. Acknowledge + `ALERT_ACKNOWLEDGE` audit in one TX.
- Do not change Story 3.1–3.3 contracts or adjustment/receipt error mapping.

**Never:**
- Dashboard widgets, notifications, export, sales/finance reports, transfers.
- Editing an already-applied migration.
- A new `ErrorCode` or settings table.

</frozen-after-approval>
