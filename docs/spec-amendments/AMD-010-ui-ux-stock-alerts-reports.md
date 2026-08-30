# AMD-010 — Phase 3 Stock Alerts and inventory reports

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** SCR-014, §17, §36, SCR-026 (inventory slice)
**Status:** **Approved** — Story 3.4
**Date:** 2026-08-30
**Companion:** [ADR-020](../adr/ADR-020-story-3-4-alerts-and-reports.md),
[AMD-009](AMD-009-rest-api-inventory-alerts-reports.md)

---

## 1. The gap

SCR-014 (MUST) is Stock Alerts. §17 requires filter and acknowledge. §36
requires current quantity, minimum, suggested action (stock) and expired /
expiring-soon with a configurable threshold (expiry). SCR-026 is the general
reports screen; Phase 3 only has inventory report APIs.

---

## 2. Approved change — SCR-014

- Route: `/inventory/alerts`
- Permission: `INVENTORY_READ`
- Columns: product, type, quantity, minimum (low stock), batch / expiry date
  (expiry), store, status, suggested action
- Status is a **label** (colour is not the only signal)
- Filter by type (all / low stock / expiry) and status (all / open / acknowledged)
- Expiry window control: 7 or 30 days
- Acknowledge action on an open alert
- SCR-010 offers a link to this screen

No dashboard expiry/low-stock widgets in this story.

---

## 3. Approved change — Phase 3 SCR-026

Route `/reports` shows only inventory reports for callers with
`REPORT_INVENTORY`: current stock (optional low-stock filter), movements, and
expiry. Sales and finance reports remain later stories.

The UI displays API fields. It does not compute authoritative stock or days
remaining.

---

## 4. Out of scope

- SCR-002 dashboard widgets
- Notifications (SCR-027)
- Export
- Transfer UI
