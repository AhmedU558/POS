# AMD-017 — Supplier-product association contracts

**Target document:** POS REST API Specification v1.0
**Sections affected:** §13 (Supplier APIs)
**Status:** **Approved** — Story 4.4 / D1–D5
**Date:** 2026-08-31
**Companion:** [ADR-024](../adr/ADR-024-story-4-4-supplier-products.md),
[AMD-018](AMD-018-ui-ux-supplier-products.md)

## 1. The gap

§13 lists GET/PUT `/suppliers/{id}/products` without bodies or row shape.

## 2. Approved change — list

**GET** `/api/v1/suppliers/{id}/products` — `SUPPLIER_READ`.

Unknown supplier → 404. Returns the current associations (list, not paged).

**Row:** `id`, `productId`, `sku`, `name`, `active`.

## 3. Approved change — replace set

**PUT** `/api/v1/suppliers/{id}/products` — `SUPPLIER_WRITE`.

| Field | Required | Rules |
|-------|----------|-------|
| `productIds` | yes | Array of product UUIDs. Duplicates collapsed. Empty array clears the set. Unknown product → 404 |

Replaces the supplier's associations in one transaction. Response is the same
list as GET. Audit: `SUPPLIER_PRODUCTS_UPDATED` on `Supplier`.

## 4. Out of this amendment

`GET /suppliers/{id}/statement`, purchase orders, goods receipts, invoices, AP.
No new `ErrorCode`. No `storeId`.

## 5. Errors

| Condition | Status | Code |
|-----------|--------|------|
| Missing `productIds` | 400 | `VALIDATION_ERROR` |
| Missing `SUPPLIER_READ` or `SUPPLIER_WRITE` | 403 | `ACCESS_DENIED` |
| Unknown supplier or product | 404 | `RESOURCE_NOT_FOUND` |
