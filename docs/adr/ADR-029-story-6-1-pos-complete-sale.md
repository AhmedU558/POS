# ADR-029: Story 6.1 POS complete sale

**Status:** Accepted — Phase 6, Story 6.1
**Date:** 2026-08-31

D1–D7: `POST /sales` + `GET /sales/{id}` + SCR-003; open `register_sessions` required; exclusive catalog tax; discount 0; CASH only; `SALE_CREATE`/`SALE_READ` for Super Admin, Store Manager, Cashier; Idempotency-Key; barcode via existing product search.

CASH payment amount is the server `grandTotal`.
