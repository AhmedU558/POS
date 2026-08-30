# ADR-024: Story 4.4 supplier-product associations

**Status:** Accepted — Phase 4, Story 4.4
**Date:** 2026-08-31
**Depends on:** [AMD-017](../spec-amendments/AMD-017-rest-api-supplier-products.md),
[AMD-018](../spec-amendments/AMD-018-ui-ux-supplier-products.md),
[ADR-023](ADR-023-story-4-3-supplier-profiles.md)

## Context

SUP-002 (SHOULD) requires associating products with suppliers. The ERD names
`supplier_products` as the relationship plus "supplier-specific information"
but lists no extra columns. REST names GET/PUT `/suppliers/{id}/products`.
This is a gap, not a contradiction: no document names supplier SKU, cost, or
lead time.

## Decision

| ID | Decision |
|----|----------|
| **D1** | `supplier_products` columns are only: `id`, `supplier_id`, `product_id`, `created_at`, `updated_at`. Unique `uk_supplier_products_supplier_id_product_id`. No supplier-specific commercial columns. |
| **D2** | Endpoints are only GET (list) and PUT (replace the set) on `/suppliers/{id}/products`. Permission-only (`SUPPLIER_READ` / `SUPPLIER_WRITE`). No store scope. No new permission codes. |
| **D3** | PUT body is `{ "productIds": [uuid...] }`. Unknown supplier or product → 404. Duplicate ids are collapsed. Empty list clears associations. GET/PUT response rows: `id`, `productId`, `sku`, `name`, `active` (catalog identity, not new association fields). |
| **D4** | PUT is audited as `SUPPLIER_PRODUCTS_UPDATED` on `Supplier` in the same write transaction. GET is a read. |
| **D5** | SCR-018 profile shows the association list. Catalog picker needs `PRODUCT_READ`; save needs `SUPPLIER_WRITE`. No PO/AP/statement UI. |

## Consequences

- V24 creates `supplier_products`.
- Purchasing, receipts, invoices, AP, and statements stay out.

## What this ADR does not change

Story 4.3 supplier CRUD, V22/V23, and catalog contracts remain as previously
decided.
