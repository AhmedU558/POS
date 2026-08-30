# AMD-009 — Stock alerts and inventory report contracts

**Target document:** POS REST API Specification v1.0
**Sections affected:** §10 (Inventory APIs), § reports table, §26, §31
**Status:** **Approved** — Story 3.4
**Date:** 2026-08-30
**Companion:** [ADR-020](../adr/ADR-020-story-3-4-alerts-and-reports.md),
[AMD-010](AMD-010-ui-ux-stock-alerts-reports.md)

---

## 1. The gap

§10 already lists `GET /inventory/alerts` and
`PATCH /inventory/alerts/{id}/acknowledge` (`INVENTORY_READ`). The reports
table lists three inventory report GETs (`REPORT_INVENTORY`). There are no
query parameters, bodies, or permission grants.

---

## 2. Approved change — list alerts

**GET** `/api/v1/inventory/alerts`

Permission: `INVENTORY_READ`. Store scope: caller must be assigned to `storeId`.

| Param | Required | Meaning |
|-------|----------|---------|
| `storeId` | yes | Store filter and scope key |
| `alertType` | no | `LOW_STOCK` or `EXPIRY` |
| `status` | no | `OPEN` or `ACKNOWLEDGED` |
| `days` | no | Expiry window used when refreshing expiry alerts. Default `7`. ≥ 0. |
| standard pageable | no | Same paging as other inventory GETs |

The service refreshes alerts from current balances and batches, then returns
the page.

**Response row:** `id`, `storeId`, `storeName`, `productId`, `productName`,
`sku`, `batchId`, `batchNumber`, `alertType`, `quantity`, `minimumLevel`,
`expirationDate`, `status`, `suggestedAction`, `daysRemaining`, `createdAt`,
`acknowledgedAt`.

Expiry rows identify product, batch, expiry date, and quantity (EXP-005).
Low-stock rows identify current quantity and minimum level.

---

## 3. Approved change — acknowledge

**PATCH** `/api/v1/inventory/alerts/{id}/acknowledge`

Permission: `INVENTORY_READ`. Store scope on the alert’s `storeId`.
No request body. Sets `status` to `ACKNOWLEDGED`. Re-acknowledge is idempotent.

Audit action: `ALERT_ACKNOWLEDGE` on `StockAlert`.

| Condition | Status | Code |
|-----------|--------|------|
| No / invalid token | 401 | `AUTHENTICATION_REQUIRED` |
| Missing `INVENTORY_READ` or no access to the alert’s store | 403 | `ACCESS_DENIED` |
| Unknown id | 404 | `RESOURCE_NOT_FOUND` |

---

## 4. Approved change — inventory reports

All three require `REPORT_INVENTORY` and `storeId` (store-scoped).

**GET** `/api/v1/reports/inventory`

Paged current stock. Optional `lowStockOnly=true` returns rows at or below
`min_stock`. Each row is the Story 3.1 balance fields plus `minStock` and
`belowMinimum`.

**GET** `/api/v1/reports/inventory/movements`

Paged ledger rows (same shape as `GET /inventory/{productId}/movements`).
Optional `productId`.

**GET** `/api/v1/reports/inventory/expiry`

Same row shape and `days` rule as `GET /inventory/expiry` (AMD-007).
Permission is `REPORT_INVENTORY`, not `INVENTORY_READ`.

Missing `REPORT_INVENTORY` or wrong store → 403 `ACCESS_DENIED`.

---

## 5. Approved change — §31

| Endpoint | Transaction Requirement |
|----------|-------------------------|
| PATCH /inventory/alerts/{id}/acknowledge | Alert status + audit. |

Stock-write generation of alerts stays inside the existing adjustment/receipt
transaction.

---

## 6. Out of scope

- `GET /notifications` and dashboard widgets
- Sales, cash, payables, and finance reports
- Export (RPT-008)
- Transfers
- Changing Story 3.1–3.3 DTOs
