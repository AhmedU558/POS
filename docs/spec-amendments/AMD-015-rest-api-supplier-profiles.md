# AMD-015 — Supplier profile CRUD contracts

**Target document:** POS REST API Specification v1.0
**Sections affected:** §13 (Supplier APIs), §5.4, §26, §30
**Status:** **Approved** — Story 4.3 / D1–D6
**Date:** 2026-08-31
**Companion:** [ADR-023](../adr/ADR-023-story-4-3-supplier-profiles.md),
[AMD-016](AMD-016-ui-ux-supplier-profiles.md)

## 1. The gap

§13 lists supplier endpoints without query parameters, bodies, or role grants.
`suppliers` has no `store_id`, so §30 cannot be applied as a store-id check.

## 2. Approved change — list and get

**GET** `/api/v1/suppliers`

Permission: `SUPPLIER_READ`. Not store-scoped.

| Param | Required | Meaning |
|-------|----------|---------|
| `query` | no | Case-insensitive match on `supplier_code`, `name`, `phone`, or `email` |
| `isActive` | no | Filter by `is_active` |
| standard pageable | no | Same paging as customer search |

**GET** `/api/v1/suppliers/{id}` — `SUPPLIER_READ`. `RESOURCE_NOT_FOUND` if missing.

**Response body:** `id`, `supplierCode`, `name`, `phone`, `email`, `address`,
`active`, `createdAt`, `updatedAt`.

## 3. Approved change — create and update

**POST** `/api/v1/suppliers` — `SUPPLIER_WRITE`.

| Field | Required | Rules |
|-------|----------|-------|
| `supplierCode` | yes | Non-blank. Unique → 409 `CONFLICT` |
| `name` | yes | Non-blank |
| `phone` | no | |
| `email` | no | |
| `address` | no | |
| `isActive` | yes | |

**PATCH** `/api/v1/suppliers/{id}` — `SUPPLIER_WRITE`. Same fields as create.
Deactivation is `isActive: false` on this PATCH. No `DELETE`. Duplicate code → 409.

Create audit: `SUPPLIER_CREATED`. Update audit: `SUPPLIER_UPDATED`. Entity type
`Supplier`.

## 4. Out of this amendment

`GET /suppliers/{id}/products`, `PUT /suppliers/{id}/products`,
`GET /suppliers/{id}/statement`. No `storeId` parameter. No new `ErrorCode`.

## 5. Errors

| Condition | Status | Code |
|-----------|--------|------|
| Missing/invalid fields | 400 | `VALIDATION_ERROR` |
| Missing `SUPPLIER_READ` or `SUPPLIER_WRITE` | 403 | `ACCESS_DENIED` |
| Unknown id | 404 | `RESOURCE_NOT_FOUND` |
| Duplicate `supplierCode` | 409 | `CONFLICT` |
