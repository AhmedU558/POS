# AMD-025 — Supplier payment and statement API

**Target:** POS REST API Specification §15
**Status:** **Approved** — Story 5.4

`POST/GET /accounts-payable/payments` (`AP_PAYMENT_CREATE` / `AP_READ`).
`GET /accounts-payable/overdue`, `GET /accounts-payable/summary` (`AP_READ`).
`GET /suppliers/{id}/statement` (`AP_READ`).

POST `{ invoiceId, amount, paymentDate, method, reference }`.
One TX: payment + `paid_amount`/`status` update + `SUPPLIER_PAYMENT_CREATED`.
Amount over remaining, or payment on non-OPEN invoice → 422.
Unknown invoice/supplier → 404.
No Idempotency-Key in this story.
