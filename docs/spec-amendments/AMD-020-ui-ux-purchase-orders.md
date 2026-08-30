# AMD-020 — Purchase orders on SCR-019

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** §19, SCR-019
**Status:** **Approved** — Story 5.1 / D1–D5
**Date:** 2026-08-31
**Companion:** [ADR-025](../adr/ADR-025-story-5-1-purchase-order-lifecycle.md)

## 1. Screens

`/purchase-orders` list (PO number, supplier, labelled status).
`/purchase-orders/new` create. `/purchase-orders/[id]` detail with line items.

Draft-only edit. Submit/cancel on draft when `PURCHASE_APPROVE`. Statuses shown:
Draft, Submitted, Cancelled. No invoice, receipt, or outstanding UI.

## 2. Out of this amendment

SCR-012 receiving, SCR-020/021 payables.
