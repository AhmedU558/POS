# ADR-018: Story 3.2 is standalone stock receiving; transfers are deferred

**Status:** Accepted — Phase 3, Story 3.2
**Date:** 2026-08-30
**Depends on:** [AMD-005](../spec-amendments/AMD-005-rest-api-inventory-receipts.md),
[AMD-006](../spec-amendments/AMD-006-ui-ux-phase-3-stock-receiving.md)

## Context

Story 3.2 discovery found that the approved documents do not agree on what “stock receiving”
and “transfers” mean for Phase 3:

- Implementation Plan Phase 3 lists both stock receiving and transfers. Sprint 5 lists
  receiving with batches, expiry, and alerts, and does not mention transfers. The later
  roadmap lists “multi-store transfers” as post-MVP.
- REST API Specification §10 defines both `POST /inventory/receipts` and
  `POST /goods-receipts` (same permission), plus create/receive transfer endpoints.
  Only `/goods-receipts` appears in the §31 transaction table. Neither receipt nor
  transfer has a request DTO.
- Database Design places `goods_receipts` under Purchasing as a child of
  `purchase_orders`. `stock_transfers` / `stock_transfer_items` have purpose text and
  no column contract.
- UI/UX SCR-012 and workflow 32.3 describe PO-based receiving with supplier, batch,
  expiry, and discrepancy. There is no transfer screen.
- SRS INV-003 (MUST) is stock receiving. INV-005 (SHOULD) is transfers “where multiple
  locations are enabled.” No such configuration exists. PUR-002 (MUST) is receiving
  against purchases — Phase 5.

Story 3.1 already shipped balances, the immutable ledger, and adjustments. Suppliers,
purchase orders, batches, and goods-receipt tables do not exist. Implementing the
PO-shaped screen or transfer lifecycle now would violate phase discipline or invent
schema.

The product owner approved the Story 3.2 decisions on 2026-08-30. This ADR records
that reading so the contradictions are not re-opened during implementation.

## Decision

| ID | Decision |
|----|----------|
| **D1** | Story 3.2 implements standalone `POST /api/v1/inventory/receipts`. Full PO / goods receiving remains Phase 5 (`POST /goods-receipts`, `goods_receipts` tables, SCR-019 / workflow 32.3). |
| **D2** | Stock transfers are **deferred**. Do not implement transfer APIs, tables, UI, or lifecycle in Story 3.2. INV-005, `POST /inventory/transfers`, `POST /inventory/transfers/{id}/receive`, and `stock_transfers` / `stock_transfer_items` remain specified for a later story. |
| **D3** | Transfer schema and lifecycle stay unspecified until that later story. No column list is approved here. |
| **D4** | Receipt request body is `{ storeId, productId, quantity }` with `quantity > 0`. No `Idempotency-Key`, `unitCost`, `reason`, or `batchId`. Contract: AMD-005. |
| **D5** | Batch / expiry on receiving is deferred to Story 3.3. Phase 3 SCR-012 does not capture them. Screen contract: AMD-006. |
| **D6** | When a stock-changing operation cannot complete because required stock is unavailable, the API returns `INSUFFICIENT_STOCK` (409). Story 3.2 receipts only increase stock, so this code is not used on the happy receipt path. It is the inventory-module convention for later decreasing operations (transfers, sales). Story 3.1 adjustments keep `BUSINESS_RULE_VIOLATION` for a resulting negative balance. |
| **D7** | Seed `INVENTORY_RECEIVE` and grant it to Super Administrator, Store Manager, and Inventory Manager only — the same three roles that hold `INVENTORY_ADJUST`. |
| **D8** | Audit action for a successful receipt is `STOCK_RECEIPT`. Record actor, target, timestamp, and before/after quantity using the existing `AuditEvent` `oldValues` / `newValues` JSON payloads. |
| **D9** | New receipt writes reject an inactive product or inactive store with `RESOURCE_INACTIVE`. Do not change Story 3.1 adjustment behaviour. |

## Consequences

- Story 3.2 needs no new business tables. The next migration seeds `INVENTORY_RECEIVE`
  and its role grants only. Receipts persist as an `inventory_transactions` row of type
  `RECEIPT` plus an `inventory_balances` update, in one transaction with the audit row.
- `InventoryTransaction.reference_type` / `reference_id` stay unset on standalone
  receipts. There is no receipt header to reference. SAD §8 “source/reference” is
  satisfied by `transaction_type = RECEIPT` and the audit record.
- SCR-012 in Phase 3 is product, quantity, confirm, and resulting stock — not PO
  comparison. The full receiving workflow remains Phase 5.
- Identity seed tests that enumerate permission codes must be updated when the new
  code is seeded. That update belongs to Story 3.2.
- Transfers, goods receipts, batches, expiry, and alerts are out of this story even
  if later Phase 3 stories implement them.

## What this ADR does not change

The approved specifications still contain transfer endpoints, `stock_transfers`
tables, PO-shaped SCR-012 text, and PUR-002. Those remain binding for their phases
and later stories. AMD-005 and AMD-006 are the only specification addenda this
decision requires.
