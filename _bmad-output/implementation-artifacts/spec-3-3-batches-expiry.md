---
title: 'Story 3.3 — Batches & Expiry'
type: 'feature'
created: '2026-08-30'
status: 'approved'
baseline_commit: '24178b7'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-019-story-3-3-batches-expiry.md'
  - '{project-root}/docs/spec-amendments/AMD-007-rest-api-inventory-batches.md'
  - '{project-root}/docs/spec-amendments/AMD-008-ui-ux-batches-expiry.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** INV-009 / INV-010 / EXP-001–EXP-003 require batch/lot and expiration
tracking. Story 3.2 receipts have no lot fields. There is no `inventory_batches`
table and no way to list expired or approaching stock.

**Approach:** Add `inventory_batches` (ADR-019 columns), `GET /inventory/batches`,
`GET /inventory/expiry`, and lot/expiry capture on receipts when the product
flags require it. SCR-013 lists batches with derived status. Reuse Story 3.2
lock → balance → ledger → audit; extend that transaction with the batch row.

Binding addenda: ADR-019, AMD-007, AMD-008. D5 from ADR-018 is the receiving
reservation.

## Boundaries & Constraints

**Always:**
- Columns only as ADR-019 D1. Specified index and `inventory_transactions.batch_id` FK.
- Lists: `INVENTORY_READ` + `StoreScopeEvaluator`. `storeId` required. `days` default 7, ≥ 0.
- Receipts: optional `batchNumber`, `expirationDate`, `manufacturingDate`. Required per product flags (ADR-019 D4). No `batchId` on the receipt body.
- Same-lot receipt adds quantity under a pessimistic batch lock.
- Story 3.1 adjustments and Story 3.2 three-field receipts (non-tracking products) stay unchanged.
- Status is derived, not stored. Expiry “today” uses the store timezone.
- DTOs at the controller; `ApiResponse` / `ApiException` + documented `ErrorCode`.
- `NUMERIC` / `BigDecimal` only for quantity.

**Ask First:**
- A `POST /inventory/batches` or a persisted batch status column.
- Adjustment `batchId` (would reopen Story 3.1).
- A new `ErrorCode` or settings table for expiry windows.
- Changing Story 3.1 `isActive` / negative-stock behaviour.

**Never:**
- `stock_alerts`, acknowledge, inventory reports, dashboard expiry widget.
- Transfers, goods receipts, purchase orders.
- Editing an already-applied migration.
- Reopening Story 3.1 or changing non-tracking Story 3.2 receipt behaviour.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error Handling |
|---|---|---|---|
| List batches | `INVENTORY_READ`, in-scope `storeId` | Paged rows with derived status | 403 if no permission / wrong store |
| Expiry filter | `days=7` or `30` | Only batches with expiry ≤ today+days | `days` < 0 → 400 `VALIDATION_ERROR` |
| Receive tracking product | lot (+ expiry if flagged) | Balance += qty; batch += qty; ledger `batch_id` set; `STOCK_RECEIPT` | Missing lot/expiry → 422 |
| Receive non-tracking | `{storeId,productId,quantity}` | Story 3.2 behaviour; no batch row | Unchanged |
| Same lot again | Same store/product/batchNumber | Batch and store qty add | Date mismatch → 422 |
| Concurrent same-lot receipts | Parallel POSTs | Final batch qty = sum | Pessimistic lock |
| Audit failure on batched receipt | Failing `AuditRecorder` | No balance, ledger, or batch row | Same TX |
| Cashier lists batches | No `INVENTORY_READ` | Rejected | 403 `ACCESS_DENIED` |

</frozen-after-approval>
