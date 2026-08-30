---
title: 'Story 3.2 — Stock Receiving'
type: 'feature'
created: '2026-08-30'
status: 'in-review'
baseline_commit: '23822f5'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-018-story-3-2-stock-receiving-scope.md'
  - '{project-root}/docs/spec-amendments/AMD-005-rest-api-inventory-receipts.md'
  - '{project-root}/docs/spec-amendments/AMD-006-ui-ux-phase-3-stock-receiving.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** INV-003 requires stock receiving. Story 3.1 shipped balances, the ledger,
and adjustments only. There is no way to increase stock as a receipt.

**Approach:** Add standalone `POST /api/v1/inventory/receipts` and Phase 3 SCR-012.
Reuse the Story 3.1 lock → balance update → immutable ledger → audit transaction.
Do not introduce purchase orders, goods-receipt tables, transfers, or batches.

Product-owner decisions D1–D9 are recorded in ADR-018. AMD-005 and AMD-006 are the
binding API and UI addenda.

## Boundaries & Constraints

**Always:**
- Request body is exactly `{ storeId, productId, quantity }` with `quantity > 0`.
- Permission `INVENTORY_RECEIVE` on the controller; store scope via
  `StoreScopeEvaluator` (ADR-017). No role-name checks.
- Inactive product or inactive store → `RESOURCE_INACTIVE`. Missing product/store →
  `RESOURCE_NOT_FOUND`. Wrong store or missing permission → `ACCESS_DENIED`.
- One `@Transactional` service method: pessimistic lock on
  `(product_id, store_id)`, increase balance, append `TransactionType.RECEIPT`
  ledger row, record `STOCK_RECEIPT` with before/after quantity JSON.
- Grant `INVENTORY_RECEIVE` only to Super Administrator, Store Manager, and
  Inventory Manager.
- Phase 3 SCR-012: product, quantity, confirm, display API-returned quantity.
- Story 3.1 adjustment code, tests, and negative-stock error mapping stay unchanged.
- DTOs at the controller; `ApiResponse` / `ApiException` + documented `ErrorCode`.
- `NUMERIC` / `BigDecimal` only for quantity.

**Ask First:**
- Any field beyond `{ storeId, productId, quantity }`.
- `Idempotency-Key`, `unitCost`, `reason`, `batchId`, or line items.
- A new business table (receipt header, transfer tables, batches).
- Changing Story 3.1 adjustment behaviour, including `isActive` checks.
- Implementing any transfer endpoint or screen.
- A new `ErrorCode`.

**Never:**
- `POST /goods-receipts`, `GET /goods-receipts/{id}`, suppliers, purchase orders.
- `POST /inventory/transfers`, `POST /inventory/transfers/{id}/receive`.
- `stock_transfers`, `stock_transfer_items`, `goods_receipts*`, `inventory_batches`,
  `stock_alerts`.
- Batch / expiry capture (Story 3.3).
- Stock alerts or inventory reports (Story 3.4).
- Editing an already-applied migration.
- Reopening Story 3.1.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error Handling |
|---|---|---|---|
| Happy receipt | Active product/store, qty > 0, permitted + in scope | Balance += qty; `RECEIPT` ledger row; `STOCK_RECEIPT` audit with before/after qty; balance DTO returned | N/A |
| First receipt for pair | No `inventory_balances` row yet | Row created at qty; one ledger row | Same unique `(product_id, store_id)` race as 3.1 |
| Qty ≤ 0 or missing fields | `0`, negative, or omitted | Rejected | 400 `VALIDATION_ERROR` |
| Unauthenticated | No / bad token | Rejected | 401 `AUTHENTICATION_REQUIRED` |
| Cashier / no `INVENTORY_RECEIVE` | Valid body | Rejected | 403 `ACCESS_DENIED` |
| Wrong store | Permitted user, store not in `user_stores` | Rejected | 403 `ACCESS_DENIED` |
| Unknown product or store | Random UUID | Rejected | 404 `RESOURCE_NOT_FOUND` |
| Inactive product | `products.is_active = false` | Rejected; no ledger/balance write | 409 `RESOURCE_INACTIVE` |
| Inactive store | `stores.is_active = false` | Rejected; no ledger/balance write | 409 `RESOURCE_INACTIVE` |
| Concurrent receipts | Same product+store, parallel POSTs | Final qty = sum of successes; ledger count = successes | Pessimistic lock |
| Caller rolls back | Failure after ledger/audit | No balance, ledger, or audit row remains | Same transaction |
| Adjustment after receipt | Existing 3.1 `POST /adjustments` | Unchanged behaviour, including no `isActive` check | Do not modify 3.1 |

</frozen-after-approval>

## Code Map

- `V12__seed_inventory_permissions.sql` — `INVENTORY_READ`, `INVENTORY_ADJUST` only. Next
  file is `V14__seed_inventory_receive_permission.sql` (name may vary; no DDL tables).
- `V13__create_inventory_tables.sql` — `inventory_balances`, `inventory_transactions`,
  immutability triggers. Read-only.
- `InventoryController` — four 3.1 mappings; add `POST /receipts` only.
- `InventoryService.adjustStock` — pattern to copy, not edit: scope, lock, `addQuantity`,
  ledger, audit. Receipts must check `product.isActive()` and `store.isActive()`.
- `InventoryTransaction` constructor used by adjustments does not set
  `reference_type` / `reference_id`. Standalone receipts leave those null (ADR-018).
- `TransactionType.RECEIPT` already exists and is unused.
- `AuditEvent` supports `oldValues` / `newValues` JSON; 3.1 adjustments call
  `AuditEvent.of(...)` without payloads. Receipts must pass quantity snapshots.
- `IdentitySeedDataTests` hard-codes the permission list and Super Administrator /
  Store Manager / Inventory Manager grants. Update those assertions in this story.
- `PermissionCode.java` still has no inventory constants (3.1 seeded SQL only).
- Frontend: `frontend/src/lib/api/inventory.ts`, `frontend/src/app/inventory/page.tsx`
  (add Receive Stock link), new receive page beside `inventory/adjust`.
- Tokens: `frontend/src/styles/tokens.css`. Do not add Tailwind or a component library.

## Tasks & Acceptance

**Execution:** (unchecked until implementation is approved)

- [x] Flyway seed for `INVENTORY_RECEIVE` and the three role grants; update identity
      seed tests.
- [x] Request DTO + `POST /api/v1/inventory/receipts` on `InventoryController`.
- [x] `InventoryService.receiveStock` — scope, active checks, lock, balance, ledger,
      `STOCK_RECEIPT` audit with before/after quantity.
- [x] API integration tests for the I/O matrix rows above that are HTTP-visible.
- [x] Concurrency test: parallel receipts on one product/store.
- [x] Security mutations: strip `@PreAuthorize`, skip store-scope, skip ledger,
      skip active check — each must turn a test red; restore the file hash.
- [x] Frontend Phase 3 SCR-012 and SCR-010 entry link; component tests for permission
      gating and submit-to-API behaviour.

**Acceptance Criteria:**

- Given an authorized Inventory Manager assigned to a store, when they POST a
  positive quantity for an active product, that store’s on-hand quantity increases
  by exactly that amount and a `RECEIPT` ledger row exists (INV-003, INV-002).
- Given the same call, a `STOCK_RECEIPT` audit row exists in the same transaction
  with actor, target, and before/after quantity (AUD-001, AUD-002, D8).
- Given a cashier or a user without store assignment, the call is 403 and stock
  does not change.
- Given an inactive product or store, the call is 409 `RESOURCE_INACTIVE` and stock
  does not change (D9).
- Given quantity ≤ 0, the call is 400 `VALIDATION_ERROR`.
- SCR-012 lets an authorized user select a product, enter a quantity, confirm, and
  see the quantity the API returned (AMD-006).
- No transfer, goods-receipt, batch, or alert surface is added.
- Existing Story 3.1 tests remain green without modification of adjustment behaviour.

## Design Notes

**No receipt header table.** Database Design has no standalone receipt entity.
`goods_receipts` belongs to purchasing. ADR-018 accepts ledger type `RECEIPT` plus
audit as the receipt record.

**Route convention.** Story 3.1 used `/inventory/adjust` for SCR-011. Phase 3
SCR-012 should use `/inventory/receive` unless implementation is told otherwise.

**D6 is recorded, not exercised on the happy receipt path.** Receipts only add
stock. `INSUFFICIENT_STOCK` stays the convention for later decreasing operations.

**Do not “fix” 3.1.** Adjustments still omit `isActive` checks and still use
`BUSINESS_RULE_VIOLATION` for a negative result.

## Verification

**Commands (run during implementation, not now):**

- `cd backend && mvn -B test`
- Frontend test script already used by Story 3.1
- Mutation script following `scratch/run_story2_1_mutations.ps1`, targeting receipt
  authorization, store scope, ledger write, and active-resource checks

**Do not** report the suite as passing without running it.
