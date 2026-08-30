# AMD-019 — Purchase order API contract

**Target document:** POS REST API Specification v1.0
**Sections affected:** §14
**Status:** **Approved** — Story 5.1 / D1–D5
**Date:** 2026-08-31
**Companion:** [ADR-025](../adr/ADR-025-story-5-1-purchase-order-lifecycle.md)

## 1. Endpoints

`GET/POST /purchase-orders`, `GET/PATCH /purchase-orders/{id}`,
`POST /purchase-orders/{id}/submit`, `POST /purchase-orders/{id}/cancel`.

List filters: `query` (PO number), `status`, pageable.

Write body: `{ poNumber, supplierId, notes, items: [{ productId, quantity }] }`.
Quantity must be `> 0`. Unknown supplier/product → 404. Duplicate `poNumber` → 409.
Non-draft PATCH/submit/cancel → 422. GET `{id}` includes line identity (`sku`, `name`).

## 2. Out of this amendment

Goods receipts, invoices, AP, payments, statements.
