# ADR-025: Story 5.1 purchase order lifecycle

**Status:** Accepted — Phase 5, Story 5.1
**Date:** 2026-08-31
**Depends on:** [AMD-019](../spec-amendments/AMD-019-rest-api-purchase-orders.md),
[AMD-020](../spec-amendments/AMD-020-ui-ux-purchase-orders.md),
[ADR-023](ADR-023-story-4-3-supplier-profiles.md)

## Context

PUR-001 requires purchase-order create/manage. ERD names tables without columns.
REST names six endpoints without DTOs. UI lists four statuses including approved.
REST has submit/cancel only (`PURCHASE_APPROVE`). Approved D1–D5 resolve this.

## Decision

| ID | Decision |
|----|----------|
| **D1** | Statuses are `DRAFT`, `SUBMITTED`, `CANCELLED`. From DRAFT only: submit or cancel. Submit is approval. No `APPROVED`. |
| **D2** | Header: `id`, `po_number` (unique), `supplier_id`, `status`, `notes`, timestamps. Lines: `id`, `purchase_order_id`, `product_id`, `quantity` (`NUMERIC`, `> 0`). No unit price or money totals. |
| **D3** | No `store_id`. Permission-only. |
| **D4** | Seed `PURCHASE_READ`, `PURCHASE_WRITE`, `PURCHASE_APPROVE` for Super Administrator, Store Manager, Inventory Manager. Not Accountant or Cashier. |
| **D5** | Audit `PURCHASE_ORDER_CREATED` / `UPDATED` / `SUBMITTED` / `CANCELLED` on `PurchaseOrder` in the write TX. |

## Consequences

- V25 creates the tables. V26 seeds permissions.
- Receiving, invoices, AP, payments, and statements stay out.

## What this ADR does not change

Stories 4.1–4.4 and V1–V24 remain as previously decided.
