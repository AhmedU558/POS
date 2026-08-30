# ADR-019: Story 3.3 batches and expiry — derived schema and reserved contract

**Status:** Accepted — Phase 3, Story 3.3
**Date:** 2026-08-30
**Depends on:** [ADR-018](ADR-018-story-3-2-stock-receiving-scope.md) D5,
[AMD-005](../spec-amendments/AMD-005-rest-api-inventory-receipts.md) §5,
[AMD-007](../spec-amendments/AMD-007-rest-api-inventory-batches.md),
[AMD-008](../spec-amendments/AMD-008-ui-ux-batches-expiry.md)

## Context

Story 3.3 is batch/lot and expiration tracking (Implementation Plan Phase 3;
SRS INV-009, INV-010, EXP-001–EXP-003; REST `GET /inventory/batches` and
`GET /inventory/expiry`; UI SCR-013). ADR-018 D5 reserved batch/expiry capture on
receiving for this story. AMD-005 §5 and AMD-006 §2 say the same.

The Database Design lists `inventory_batches` with purpose, relationships, and
`INDEX(product_id, expiration_date)`, but **no column contract**. REST lists the
two GET endpoints with permissions and no request/response DTOs. There is no
`POST /inventory/batches`. Configurable expiry windows (EXP-003, PRD “7 or 30
days”) have no settings table or API.

This ADR records the reading of those named elements so Story 3.3 does not invent
tables, permissions, or later-phase work.

## Decision

| ID | Decision |
|----|----------|
| **D1** | Create `inventory_batches` with only columns named or implied by the approved documents: `id`, `product_id`, `store_id`, `batch_number`, `quantity`, `expiration_date`, `manufacturing_date`, `created_at`. Add the specified index `idx_inventory_batches_product_id_expiration_date`. Add the specified FK `inventory_transactions.batch_id → inventory_batches.id` (the column already exists in V13 without an FK). |
| **D2** | Uniqueness of a lot at a store is `(product_id, store_id, batch_number)`. A second receipt of the same lot adds quantity under a pessimistic lock. A conflicting expiration or manufacturing date is `BUSINESS_RULE_VIOLATION`. |
| **D3** | There is no `POST /inventory/batches`. Batches are created only by `POST /inventory/receipts` when the product has `track_batch` and/or `track_expiry` (PROD-008, PUR-003, D5). Optional receipt fields are `batchNumber`, `expirationDate`, `manufacturingDate` — not `batchId`. |
| **D4** | When `track_batch` or `track_expiry` is true, `batchNumber` is required. When `track_expiry` is true, `expirationDate` is required. When both flags are false, Story 3.2’s three-field body is unchanged and extra lot fields are ignored. |
| **D5** | `GET /inventory/batches` and `GET /inventory/expiry` use existing `INVENTORY_READ` and `StoreScopeEvaluator`. Required query param: `storeId`. Optional: `productId` (batches only), `days` (window; default **7**, PRD’s first example; `30` is valid). Negative `days` is `VALIDATION_ERROR`. |
| **D6** | Expiry “today” is the current date in the store’s timezone. `GET /inventory/expiry` returns rows with a non-null `expiration_date` on or before `today + days` (expired + approaching, including today). Status is **derived**, not stored: `EXPIRED`, `EXPIRING_TODAY`, `APPROACHING`, `OK`. |
| **D7** | Story 3.1 adjustments stay unchanged (no `batchId`, no `isActive` change). Do not implement `stock_alerts`, alert acknowledge, inventory reports, dashboard widgets, sell-expired POS policy (EXP-004), or transfers. |

## Consequences

- Next migration is V15 (table + transaction FK only). No new permission seed.
- Receipt, batch quantity, store balance, `RECEIPT` ledger (`batch_id` set when a
  batch exists), and `STOCK_RECEIPT` audit stay one `@Transactional` method.
  Audit payloads remain the Story 3.2 quantity JSON (D8).
- SCR-013 lists product, batch, quantity, expiry date, store, and derived status.
  Phase 3 SCR-012 collects lot/expiry only when the selected product’s flags
  require them.

## What this ADR does not change

Transfer endpoints and tables, PO/goods receiving, `stock_alerts`,
`GET /reports/inventory/expiry`, and Story 3.1 adjustment semantics remain as
previously decided. AMD-007 and AMD-008 are the API and UI addenda for the
reserved Story 3.3 contract.
