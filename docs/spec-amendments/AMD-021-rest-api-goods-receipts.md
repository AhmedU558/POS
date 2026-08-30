# AMD-021 — Goods receipt API contract

**Target document:** POS REST API Specification v1.0
**Sections affected:** §14
**Status:** **Approved** — Story 5.2
**Date:** 2026-08-31

`POST /goods-receipts` (`INVENTORY_RECEIVE`): `{ purchaseOrderId, storeId, items[{ productId, quantity, batchNumber?, expirationDate?, manufacturingDate? }] }`.
`GET /goods-receipts/{id}` (`INVENTORY_READ`).

Unknown PO/store → 404. Non-submitted PO or product not on PO → 422. Store scope → 403.
Quantity `> 0`. Inventory errors reuse the 3.2/3.3 receive contract.
