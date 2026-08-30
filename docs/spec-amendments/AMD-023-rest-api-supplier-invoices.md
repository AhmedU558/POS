# AMD-023 — Supplier invoice API

**Target:** POS REST API Specification §15
**Status:** **Approved** — Story 5.3

`GET/POST /accounts-payable/invoices`, `GET/PATCH /accounts-payable/invoices/{id}`.
Create: `{ invoiceNumber, supplierId, invoiceDate, dueDate, totalAmount, notes }`.
PATCH (OPEN only): `{ invoiceNumber, invoiceDate, dueDate, totalAmount, notes }`.
Duplicate number → 409. Unknown supplier → 404. Non-OPEN PATCH → 422.
`remainingAmount` = `totalAmount - paidAmount` (paid defaults to 0).
