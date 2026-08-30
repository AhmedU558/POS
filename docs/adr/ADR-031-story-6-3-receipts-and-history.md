# ADR-031: Story 6.3 receipts and sales history

**Status:** Accepted — Phase 6, Story 6.3
**Date:** 2026-08-31

Receipts are derived from the sale (no `receipts` table). `GET /sales/{id}/receipt` returns that projection. `POST .../reprint` audits `RECEIPT_REPRINTED` and does not drive hardware.

`GET /sales` is store-scoped via `user_stores` and filters receipt query, status, customer, cashier, and date range. Terminal/register/payment-method filters and returns actions are omitted (Phase 8). `GET /customers/{id}/sales` is API-only so Story 4.1 profile UI stays unchanged.
