# AMD-031 — Sales history and receipts

**Target:** POS REST API Specification §16
**Status:** **Approved** — Story 6.3

`GET /sales` (`SALE_READ`) is paginated and store-scoped.
`GET /sales/{id}/receipt` (`RECEIPT_READ`) returns receipt data from the sale.
`POST /sales/{id}/receipt/reprint` (`RECEIPT_REPRINT`) records `RECEIPT_REPRINTED`.
`GET /customers/{id}/sales` (`CUSTOMER_READ`) lists that customer's store-scoped sales.
