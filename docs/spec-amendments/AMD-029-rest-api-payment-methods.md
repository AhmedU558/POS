# AMD-029 — Payment methods and split tender

**Target:** POS REST API Specification §16 / payment methods
**Status:** **Approved** — Story 6.2

`GET /payment-methods` (`PAYMENT_READ`) returns active methods.

`POST /sales` accepts one or more payments. Amounts must sum to the server `grandTotal`, except a single CASH payment whose amount is overwritten to `grandTotal`. STORE_CREDIT requires `customerId` and posts a REDEEM against the customer credit ledger.
