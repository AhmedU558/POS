# AMD-027 — POS complete sale API

**Target:** POS REST API Specification §16
**Status:** **Approved** — Story 6.1

`POST /sales` (`SALE_CREATE`) requires `Idempotency-Key`.
Body: `{ storeId, terminalId, registerId, registerSessionId, customerId?, items[{productId,quantity}], payments[{paymentMethodId,amount}] }`.
Exactly one CASH payment. Totals and payment amount are server-calculated (exclusive tax, discount 0).
Open register session required. Same key + same body returns the original sale.
`GET /sales/{id}` (`SALE_READ`), store-scoped.
