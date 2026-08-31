# UAT Validation Checklist — Phase 12

This checklist covers the UAT scenarios required by the Implementation Plan (section 22).

## 1. Authentication & RBAC (Phase 1)

- [x] Login with valid credentials → JWT returned
- [x] Login with invalid credentials → 401 error
- [x] Access protected endpoint without token → 401
- [x] Access endpoint without required permission → 403
- [x] Password change on first login enforced
- [x] Token refresh works
- [x] Rate limiting active (configurable via `app.security.rate-limit.max-tokens`)

## 2. Store & Terminal Management (Phase 2)

- [x] Create store → 201
- [x] List stores → paginated response
- [x] Create terminal for store → 201
- [x] Register CRUD operations

## 3. Product & Category Management (Phase 3)

- [x] Create category → 201
- [x] Create product with category → 201
- [x] Search products by name/SKU
- [x] Update product price

## 4. Inventory Management (Phase 4)

- [x] Receive inventory (goods receipt)
- [x] Adjust inventory (increase/decrease)
- [x] View inventory levels per store
- [x] Concurrent inventory operations handled correctly

## 5. Customer & Credit (Phase 5)

- [x] Create customer → 201
- [x] View customer credit account
- [x] Credit balance tracked via API (not frontend-computed)

## 6. Supplier & Purchasing (Phase 6)

- [x] Create supplier → 201
- [x] Create purchase order → 201
- [x] Approve purchase order
- [x] Receive goods against PO
- [x] Supplier invoice and payment

## 7. Sales & POS (Phase 7)

- [x] Open register session
- [x] Create sale with items → 201
- [x] Payment recorded
- [x] Receipt generated with receipt number
- [x] Close register session with cash count
- [x] Sale refund/return

## 8. Quotations & Promotions (Phase 8)

- [x] Create quotation → 201
- [x] Send/approve/convert quotation
- [x] Create promotion → 201
- [x] Sale discount applied

## 9. Online Orders (Phase 9)

- [x] Create online order → 201
- [x] Fulfill/cancel/refund order
- [x] Order status transitions validated

## 10. Budget & Expense (Phase 10)

- [x] Create budget → 201
- [x] Approve budget
- [x] Create expense → 201
- [x] Financial reports available

## 11. POS Hardware Integrations

> **Note**: Hardware integrations (barcode scanners, receipt printers, cash drawers)
> require physical devices. The API layer is validated; hardware-specific driver
> integration is an operational task performed during on-site deployment.

- [x] Barcode/SKU lookup endpoint responds correctly
- [x] Receipt data returned in API response (printable by client)
- [x] Register cash operations support cash drawer trigger (client-side)
- [ ] **On-site**: Physical barcode scanner input testing
- [ ] **On-site**: Physical receipt printer integration
- [ ] **On-site**: Physical cash drawer integration

## 12. Receipt/Label Printing

- [x] Receipt number generated uniquely per sale
- [x] Receipt reprint endpoint functional
- [x] Receipt data includes all required fields (items, totals, tax, store info)
- [ ] **On-site**: Print formatting verified on target printer hardware

## 13. Register Closing

- [x] Register session open/close lifecycle tested
- [x] Cash count recorded at close
- [x] Expected vs actual cash variance computed server-side
- [x] Register close permissions enforced (REGISTER_CLOSE)
- [x] Integration test: `RegisterSessionCloseApiIntegrationTests` passes

## 14. Cross-cutting

- [x] All money fields use `BigDecimal`/`NUMERIC` (never float/double)
- [x] Store-scoped resources enforce caller's permitted stores
- [x] API responses use `ApiResponse` envelope
- [x] Errors use `ApiException` + `ErrorCode`
- [x] Flyway migrations applied successfully (53 versions)
- [x] Hibernate schema validation passes

---

## Test Suite Summary

| Metric | Value |
|--------|-------|
| Total tests | 392 |
| Passed | 392 |
| Failed | 0 |
| Skipped | 0 |
| Duration | ~2:38 min |

## Validation Date

2026-08-31
