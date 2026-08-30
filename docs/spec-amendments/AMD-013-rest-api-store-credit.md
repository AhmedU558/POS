# AMD-013 — Store credit ledger contracts

**Target document:** POS REST API Specification v1.0
**Sections affected:** §12 (Customer APIs), §5.4, §26, §29, §30
**Status:** **Approved** — Story 4.2 / D1–D8
**Date:** 2026-08-31
**Companion:** [ADR-022](../adr/ADR-022-story-4-2-store-credit-ledger.md),
[AMD-014](AMD-014-ui-ux-store-credit.md)

## 1. The gap

§12 lists `GET /customers/{id}/credit` and
`POST /customers/{id}/credit/transactions` without bodies, query parameters,
or role grants. The ERD gives `customer_credits` no `store_id`, so §30 cannot
be applied as a store-id check. SCR-017 needs a ledger list; REST does not
name a third endpoint.

## 2. Approved change — get store credit

**GET** `/api/v1/customers/{id}/credit`

Permission: `CREDIT_READ`. Not store-scoped.

Unknown customer → 404 `RESOURCE_NOT_FOUND`. Missing credit account is not an
error: `balance` is `0`, `currencyCode` and `status` are null, `transactions`
is empty.

| Param | Required | Meaning |
|-------|----------|---------|
| standard pageable | no | Pages the embedded ledger, newest first |

**Response body:** `customerId`, `customerCode`, `name`, `creditLimit`
(from the profile; not a cap), `balance`, `currencyCode`, `status`,
`transactions` (Spring page of ledger rows).

**Ledger row:** `id`, `transactionType`, `amount` (signed posted delta),
`referenceType`, `referenceId`, `balanceAfter`, `createdAt`.

## 3. Approved change — issue / redeem / adjust

**POST** `/api/v1/customers/{id}/credit/transactions` — `CREDIT_WRITE`.

| Field | Required | Rules |
|-------|----------|-------|
| `transactionType` | yes | `ISSUE`, `REDEEM`, or `ADJUST` |
| `amount` | yes | ISSUE/REDEEM: `> 0`. ADJUST: nonzero (sign is direction) |
| `currencyCode` | first write only | Exactly 3 letters. Later writes must match the account if sent |
| `referenceType` | no | Stored; not resolved |
| `referenceId` | no | Stored; not resolved |

Creates the `customer_credits` row on the first successful write (`status`
`ACTIVE`, `balance` after the posting). Updates `balance` and inserts one
immutable ledger row in the same transaction. Response is the same shape as
GET (ledger page 0).

Posted ledger `amount`: ISSUE `+amount`, REDEEM `−amount`, ADJUST as given.
`SUM(amount)` equals cached `balance`.

## 4. Out of this amendment

`GET /customers/{id}/statement`, `GET /customers/{id}/sales`, POS complete-sale
store-credit tender, `Idempotency-Key`. No `storeId` parameter. No new
`ErrorCode`.

## 5. Errors

| Condition | Status | Code |
|-----------|--------|------|
| Missing/invalid fields; ISSUE/REDEEM `amount` ≤ 0; ADJUST `amount` = 0; bad `currencyCode` | 400 | `VALIDATION_ERROR` |
| Missing `CREDIT_READ` or `CREDIT_WRITE` | 403 | `ACCESS_DENIED` |
| Unknown customer | 404 | `RESOURCE_NOT_FOUND` |
| Inactive customer on write | 409 | `RESOURCE_INACTIVE` |
| Currency mismatch with existing account | 409 | `CONFLICT` |
| Resulting `balance` < 0 | 422 | `BUSINESS_RULE_VIOLATION` |
