# ADR-026: Story 5.2 PO goods receiving

**Status:** Accepted — Phase 5, Story 5.2
**Date:** 2026-08-31
**Depends on:** [ADR-018](ADR-018-story-3-2-stock-receiving-scope.md),
[ADR-025](ADR-025-story-5-1-purchase-order-lifecycle.md)

## Decision

| ID | Decision |
|----|----------|
| **D1** | `POST /goods-receipts` requires a `SUBMITTED` purchase order. No supplier-only GR. |
| **D2** | Header: `id`, `purchase_order_id`, `store_id`, timestamps. Lines: `id`, `goods_receipt_id`, `product_id`, `quantity` (`> 0`), optional batch/expiry dates. |
| **D3** | One write TX: validate PO + lines on the PO → existing `InventoryService.receiveStock` (lock, balance, `RECEIPT` ledger, batch when tracked) → persist GR → `GOODS_RECEIPT_CREATED`. |
| **D4** | Existing `INVENTORY_RECEIVE` / `INVENTORY_READ`. Store scope on `store_id`. No new permissions. PO status unchanged. |
| **D5** | UI: receive from a submitted PO (`/purchase-orders/[id]/receive`). Standalone `/inventory/receive` stays as Story 3.2. |

## Out

Invoices, AP, payments, statements, transfers. V1–V26 unchanged.
