# ADR-023: Story 4.3 supplier profiles

**Status:** Accepted — Phase 4, Story 4.3
**Date:** 2026-08-31
**Depends on:** [AMD-015](../spec-amendments/AMD-015-rest-api-supplier-profiles.md),
[AMD-016](../spec-amendments/AMD-016-ui-ux-supplier-profiles.md),
[ADR-021](ADR-021-story-4-1-customer-profiles.md)

## Context

Implementation Plan Phase 4 story 4.3 is SUP-001 (supplier profiles) plus
search/filtering and SCR-018. The approved documents name `suppliers` as
"master and contact data", four CRUD endpoints, and `SUPPLIER_READ` /
`SUPPLIER_WRITE`, but do not list columns, DTOs, or role grants. This is a
gap, not a contradiction: no document assigns `store_id` or a different
contact set.

Contact fields follow the customer master (ADR-021 D2) minus `credit_limit`,
which no supplier document names. Unique `supplier_code` follows the same
master-data identity pattern as `customer_code` / store `code` / product
`sku`.

## Decision

| ID | Decision |
|----|----------|
| **D1** | Suppliers are shared master data. No `store_id`. No `storeId` query param. No `StoreScopeEvaluator`. Authorization is permission-only. |
| **D2** | `suppliers` columns are only: `id`, `supplier_code` (unique `uk_suppliers_supplier_code`), `name`, `phone`, `email`, `address`, `is_active`, `created_at`, `updated_at`. No tax, payment-term, or balance columns. |
| **D3** | Endpoints are only `GET/POST /suppliers` and `GET/PATCH /suppliers/{id}`. `GET/PUT /suppliers/{id}/products` (SUP-002 / Story 4.4) and `GET /suppliers/{id}/statement` (Phase 5) are omitted. No `DELETE`; deactivate via PATCH `isActive: false`. |
| **D4** | List filters: `query` (code/name/phone/email), `isActive`, pageable. Duplicate code → 409 `CONFLICT`. |
| **D5** | Seed `SUPPLIER_READ` and `SUPPLIER_WRITE` for Super Administrator, Store Manager, Inventory Manager, and Accountant (SCR-018 primary roles plus Super Admin). Not Cashier. |
| **D6** | Create and update are audited as `SUPPLIER_CREATED` and `SUPPLIER_UPDATED` on `Supplier` in the same write transaction. Lists are reads. |

## Consequences

- V22 creates `suppliers`. V23 seeds the two permissions.
- Supplier-product associations, POs, receipts, invoices, AP, and statements
  stay out.

## What this ADR does not change

Stories 4.1–4.2 customer/credit contracts and applied migrations remain as
previously decided.
