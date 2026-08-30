# AMD-011 — Customer profile CRUD contracts

**Target document:** POS REST API Specification v1.0
**Sections affected:** §12 (Customer APIs), §5.4, §26, §30
**Status:** **Approved** — Story 4.1 / D1–D8
**Date:** 2026-08-31
**Companion:** [ADR-021](../adr/ADR-021-story-4-1-customer-profiles.md),
[AMD-012](AMD-012-ui-ux-customer-profiles.md)

## 1. The gap

§12 lists customer endpoints without query parameters, bodies, or role grants.
`customers` has no `store_id`, so §30 cannot be applied as a store-id check.

## 2. Approved change — list and get

**GET** `/api/v1/customers`

Permission: `CUSTOMER_READ`. Not store-scoped.

| Param | Required | Meaning |
|-------|----------|---------|
| `query` | no | Case-insensitive match on `customer_code`, `name`, `phone`, or `email` |
| `isActive` | no | Filter by `is_active` |
| standard pageable | no | Same paging as catalog product search |

**GET** `/api/v1/customers/{id}` — `CUSTOMER_READ`. `RESOURCE_NOT_FOUND` if missing.

**Response body:** `id`, `customerCode`, `name`, `phone`, `email`, `address`,
`creditLimit`, `active`, `createdAt`, `updatedAt`.

## 3. Approved change — create and update

**POST** `/api/v1/customers` — `CUSTOMER_WRITE`.

| Field | Required | Rules |
|-------|----------|-------|
| `customerCode` | yes | Non-blank. Unique → 409 `CONFLICT` |
| `name` | yes | Non-blank |
| `phone` | no | |
| `email` | no | |
| `address` | no | |
| `creditLimit` | yes | `BigDecimal` ≥ 0 |
| `isActive` | yes | |

**PATCH** `/api/v1/customers/{id}` — `CUSTOMER_WRITE`. Same fields as create.
Deactivation is `isActive: false` on this PATCH. No `DELETE`. Duplicate code → 409.

Create audit: `CUSTOMER_CREATED`. Update audit: `CUSTOMER_UPDATED`. Entity type
`Customer`.

## 4. Out of this amendment

`GET /customers/{id}/sales`, `GET /customers/{id}/statement`,
`GET /customers/{id}/credit`, `POST /customers/{id}/credit/transactions`.
No `storeId` parameter.

## 5. Errors

| Condition | Status | Code |
|-----------|--------|------|
| Missing/invalid fields; `creditLimit` < 0 | 400 | `VALIDATION_ERROR` |
| Missing `CUSTOMER_READ` or `CUSTOMER_WRITE` | 403 | `ACCESS_DENIED` |
| Unknown id | 404 | `RESOURCE_NOT_FOUND` |
| Duplicate `customerCode` | 409 | `CONFLICT` |
