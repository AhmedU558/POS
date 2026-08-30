# ADR-020: Story 3.4 stock alerts and inventory reports

**Status:** Accepted — Phase 3, Story 3.4
**Date:** 2026-08-30
**Depends on:** [ADR-019](ADR-019-story-3-3-batches-expiry.md),
[AMD-009](../spec-amendments/AMD-009-rest-api-inventory-alerts-reports.md),
[AMD-010](../spec-amendments/AMD-010-ui-ux-stock-alerts-reports.md)

## Context

Implementation Plan Phase 3 remaining items after Stories 3.1–3.3 are low-stock
alerts and inventory reports. Transfers stay deferred (ADR-018 D2).

Named surfaces:

- SRS INV-008, EXP-005, RPT-003, RPT-004
- REST `GET /inventory/alerts`, `PATCH /inventory/alerts/{id}/acknowledge`
  (`INVENTORY_READ`); `GET /reports/inventory`, `/movements`, `/expiry`
  (`REPORT_INVENTORY`)
- Database `stock_alerts` purpose only — no column contract
- UI SCR-014 (MUST), §17 acknowledge, §36 Stock Alerts / Expiry Alerts;
  SCR-026 inventory slice only
- PRD: generate when current stock reaches or falls below `min_stock`

`stock_alerts` has no column list. Report endpoints have no DTOs.
`REPORT_INVENTORY` is not seeded. There is no settings table for thresholds.

## Decision

| ID | Decision |
|----|----------|
| **D1** | `stock_alerts` columns are only those named or implied: `id`, `store_id`, `product_id`, `batch_id`, `alert_type` (`LOW_STOCK` \| `EXPIRY`), `quantity`, `minimum_level`, `expiration_date`, `status` (`OPEN` \| `ACKNOWLEDGED`), `acknowledged_at`, `acknowledged_by`, `created_at`, `updated_at`. |
| **D2** | Threshold is `products.min_stock` (INV-008 / PRD). Expiry window is request `days`, default **7** (same as Story 3.3). No settings table. |
| **D3** | Alerts are generated from current inventory: after adjust/receive, and refreshed on `GET /inventory/alerts`. One low-stock row per store+product; one expiry row per store+batch. Recovering above threshold / leaving the expiry window removes the row so a later breach opens a new `OPEN` alert. Acknowledge does not reset while the condition still holds. |
| **D4** | Suggested action is derived, not stored: reorder (low stock); review expired / approaching stock (expiry). |
| **D5** | Seed `REPORT_INVENTORY` for Super Administrator, Store Manager, Inventory Manager (role: stock reports), and Accountant (SCR-026 / reports matrix). Cashier is not granted it. |
| **D6** | Report bodies reuse existing inventory shapes plus `minStock` / `belowMinimum` on the stock report. Do not change Story 3.1–3.3 response contracts. |
| **D7** | Acknowledge is audited as `ALERT_ACKNOWLEDGE`. Lists and reports are reads. Do not implement dashboard widgets, notifications, export, sales/finance reports, or transfers. |

## Consequences

- V16 creates `stock_alerts`. V17 seeds `REPORT_INVENTORY`.
- Generation shares the stock-write transaction. Acknowledge is its own
  `@Transactional` method with the audit row.
- SCR-014 is `/inventory/alerts`. Phase 3 `/reports` shows only the three
  inventory report endpoints.

## What this ADR does not change

Story 3.1 adjustment semantics, Story 3.2 non-tracking receipts, Story 3.3
batch/expiry list contracts, and deferred transfers remain as previously
decided.
