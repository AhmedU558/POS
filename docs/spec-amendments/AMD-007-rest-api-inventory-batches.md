# AMD-007 — Inventory batches, expiry list, and receipt lot fields

**Target document:** POS REST API Specification v1.0
**Sections affected:** §10 (Inventory APIs), §11 (receipt sibling of the adjustment
example), §26 (Validation Rules), §31 (Transactional API Rules)
**Status:** **Approved** — implements ADR-018 D5 and AMD-005 §5 (Story 3.3)
**Raised by:** Phase 3 Story 3.3
**Date:** 2026-08-30
**Companion:** [ADR-019](../adr/ADR-019-story-3-3-batches-expiry.md),
[AMD-008](AMD-008-ui-ux-batches-expiry.md)

---

## 1. The gap

§10 already lists:

| Method | Endpoint | Purpose | Permission |
|--------|----------|---------|------------|
| GET | `/inventory/batches` | List batches. | `INVENTORY_READ` |
| GET | `/inventory/expiry` | List expiring/expired stock. | `INVENTORY_READ` |

There are no query parameters or response bodies. AMD-005 reserved batch/expiry
fields on `POST /inventory/receipts` for Story 3.3. There is still no
`POST /inventory/batches`.

This amendment fills those contracts only. It does not add alerts, reports, or
adjustment `batchId`.

---

## 2. Approved change — list batches

**GET** `/api/v1/inventory/batches`

Permission: `INVENTORY_READ`. Store scope: caller must be assigned to `storeId`.

| Param | Required | Meaning |
|-------|----------|---------|
| `storeId` | yes | Store filter and scope key |
| `productId` | no | Restrict to one product |
| `days` | no | Approaching-expiry window for derived `status`. Default `7`. Must be ≥ 0. |
| standard pageable | no | Same paging as other inventory GETs |

**Response `data`:** paged `InventoryBatch` rows:

| Field | Meaning |
|-------|---------|
| `id` | Batch id |
| `productId`, `productName`, `sku` | Product |
| `storeId`, `storeName` | Store |
| `batchNumber` | Lot identifier |
| `quantity` | Batch quantity (`NUMERIC` / decimal) |
| `expirationDate` | Date or null |
| `manufacturingDate` | Date or null |
| `status` | `EXPIRED` \| `EXPIRING_TODAY` \| `APPROACHING` \| `OK` |
| `daysRemaining` | Whole days from store-local today to `expirationDate`; negative if expired; null if no date |

---

## 3. Approved change — list expiring / expired

**GET** `/api/v1/inventory/expiry`

Permission: `INVENTORY_READ`. Same store-scope rule.

| Param | Required | Meaning |
|-------|----------|---------|
| `storeId` | yes | Store filter and scope key |
| `days` | no | Inclusive window. Default `7`. `0` = expired and today. `30` is valid. Must be ≥ 0. |
| standard pageable | no | Same paging as other inventory GETs |

Returns batches whose `expirationDate` is not null and is on or before
store-local `today + days`. Same row shape as §2.

---

## 4. Approved change — receipt lot fields (extends AMD-005)

**POST** `/api/v1/inventory/receipts` still requires `{ storeId, productId, quantity }`
with `quantity > 0`. Story 3.3 adds **optional** fields:

```json
{
  "storeId": "uuid",
  "productId": "uuid",
  "quantity": 10.5,
  "batchNumber": "LOT-001",
  "expirationDate": "2026-12-31",
  "manufacturingDate": "2026-06-01"
}
```

| Product flags | Rule |
|---------------|------|
| `track_batch` or `track_expiry` | `batchNumber` required (non-blank) |
| `track_expiry` | `expirationDate` required |
| neither | Three-field body remains valid; lot fields ignored |

`batchId` is still **not** a receipt field (client sends the lot number; the
service finds or creates the batch).

Same-lot receipt adds quantity. A different expiration or manufacturing date for
an existing `(store, product, batchNumber)` is `BUSINESS_RULE_VIOLATION`.

When a batch is written, the `RECEIPT` ledger row sets `batch_id`. Audit action
remains `STOCK_RECEIPT` with the Story 3.2 quantity snapshots.

---

## 5. Approved change — validation and errors

Reuse existing §26 / §28 codes. Do not add error codes.

| Condition | Status | Code |
|-----------|--------|------|
| Missing `storeId`; `days` < 0; malformed query/body | 400 | `VALIDATION_ERROR` |
| No / invalid access token | 401 | `AUTHENTICATION_REQUIRED` |
| Missing `INVENTORY_READ` (lists) or `INVENTORY_RECEIVE` (receipt); or no access to `storeId` | 403 | `ACCESS_DENIED` |
| Product or store id does not exist on a write | 404 | `RESOURCE_NOT_FOUND` |
| Inactive product or store on a new receipt | 409 | `RESOURCE_INACTIVE` |
| Tracking product missing required lot/expiry; lot date mismatch | 422 | `BUSINESS_RULE_VIOLATION` |

Story 3.1 `POST /inventory/adjustments` is unchanged.

---

## 6. Approved change — §31

The existing `POST /inventory/receipts` row still applies. When lot tracking is
on, the same transaction also writes `inventory_batches` and sets
`inventory_transactions.batch_id`. The GET list endpoints are read-only.

---

## 7. Out of scope for this amendment

- `POST /inventory/batches`
- Adjustment `batchId` (REST §11 example; Story 3.1 omitted it)
- `GET /inventory/alerts`, acknowledge, `GET /reports/inventory/expiry`
- Transfer and goods-receipt contracts
