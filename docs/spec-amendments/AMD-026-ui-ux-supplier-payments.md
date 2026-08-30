# AMD-026 — SCR-021 payments and statements

**Target:** POS UI/UX Specification SCR-021, §32.5
**Status:** **Approved** — Story 5.4

Invoice detail shows Record payment when `AP_PAYMENT_CREATE` and OPEN.
`/accounts-payable/[id]/pay` captures amount, date, method, reference, then confirms.
Accounts Payable list shows invoiced/paid/outstanding/overdue and an overdue filter.
Supplier profile shows Statement when `AP_READ`.
`/suppliers/[id]/statement` lists invoices, payments, and running balance.
