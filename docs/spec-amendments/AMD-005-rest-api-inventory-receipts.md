# AMD-005 — Inventory receipt request contract

**Target document:** POS REST API Specification v1.0
**Sections affected:** §10 (Inventory APIs), §11 (Inventory Adjustment Example — sibling receipt example), §26 (Validation Rules), §31 (Transactional API Rules)
**Status:** **Approved** — 2026-08-30 by product owner
**Raised by:** Phase 3 Story 3.2 discovery
**Date:** 2026-08-30
**Companion:** [ADR-018](../adr/ADR-018-story-3-2-stock-receiving-scope.md),
[AMD-006](AMD-006-ui-ux-phase-3-stock-receiving.md)

---

## 1. The gap

§10 already lists:

| Method | Endpoint | Purpose | Permission |
|--------|----------|---------|------------|
| POST | `/inventory/receipts` | Receive stock. | `INVENTORY_RECEIVE` |

§11 gives a worked example only for `POST /inventory/adjustments`. There is no request
or response body for receipts. §31 requires `POST /inventory/adjustments` and
`POST /goods-receipts` to be atomic, and does not name `/inventory/receipts`.

Without a contract, implementers would have to invent fields (reason, batch, cost,
idempotency, line items) or treat receipts as adjustments. Discovery D1 / D4
forbade that.

This amendment fills the receipt contract only. It does not add, remove, or implement
`POST /goods-receipts` or the transfer endpoints.

---

## 2. Approved change — receipt example (add after §11)

**POST** `/api/v1/inventory/receipts`

Permission: `INVENTORY_RECEIVE`.

Store scope: the caller must be assigned to `storeId` (REST API §30, ADR-017).
Otherwise `ACCESS_DENIED`.

```json
{
  "storeId": "uuid",
  "productId": "uuid",
  "quantity": 10.5
}
```

`quantity` must be greater than zero.

The following fields are **not** part of this contract:

- `reason`
- `batchId`
- `unitCost`
- `Idempotency-Key`
- line-item arrays
- supplier or purchase-order identifiers

**Response:** standard §5.1 envelope whose `data` is the same inventory-balance
representation already returned by Story 3.1 adjustments
(`productId`, `productName`, `sku`, `storeId`, `storeName`, `quantity`, `lastUpdatedAt`).

The service must validate permission, store scope, product and store existence and
active status, and quantity rules, then create the inventory ledger entry atomically
with the balance update and the audit record.

---

## 3. Approved change — validation and errors

Apply existing §26 and §28 codes as follows. Do not add error codes.

| Condition | Status | Code |
|-----------|--------|------|
| Missing or malformed fields; `quantity` ≤ 0 | 400 | `VALIDATION_ERROR` |
| No / invalid access token | 401 | `AUTHENTICATION_REQUIRED` |
| Missing `INVENTORY_RECEIVE`, or no access to `storeId` | 403 | `ACCESS_DENIED` |
| Product or store id does not exist | 404 | `RESOURCE_NOT_FOUND` |
| Product or store exists but is inactive | 409 | `RESOURCE_INACTIVE` |
| Required stock is unavailable on a decreasing inventory operation | 409 | `INSUFFICIENT_STOCK` |

`INSUFFICIENT_STOCK` is the inventory-module code for a shortage. A valid receipt only
increases stock and does not return it. Story 3.1 `POST /inventory/adjustments` is
unchanged: a resulting negative balance remains `BUSINESS_RULE_VIOLATION`.

---

## 4. Approved change — §31 transaction table

Add this row (do not remove existing rows):

| Endpoint | Transaction Requirement |
|----------|-------------------------|
| POST /inventory/receipts | Inventory balance + ledger + audit. |

`POST /goods-receipts` stays in the table and stays Phase 5.

---

## 5. Out of scope for this amendment

- Transfer request/response bodies.
- GET list/detail for inventory receipts.
- Goods-receipt APIs and purchase-order linkage.
- Batch or expiry fields (Story 3.3).
