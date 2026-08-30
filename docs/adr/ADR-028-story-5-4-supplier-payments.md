# ADR-028: Story 5.4 supplier payments and statements

**Status:** Accepted — Phase 5, Story 5.4
**Date:** 2026-08-31
**Depends on:** [ADR-027](ADR-027-story-5-3-supplier-invoices.md)

## Decision

| ID | Decision |
|----|----------|
| **D1** | `supplier_payments`: `id`, `supplier_invoice_id`, `amount`, `payment_date`, `payment_method`, `reference`, `created_at`. POST `{ invoiceId, amount, paymentDate, method, reference }`. |
| **D2** | Partial payments allowed. `amount > 0` and `<= remainingAmount`. Reject on `PAID`/`CANCELLED`. Set `PAID` when remaining is 0. |
| **D3** | Method enum: `CASH \| BANK_TRANSFER \| CHEQUE \| OTHER`. No payment-method table. |
| **D4** | Overdue: OPEN + `dueDate < today`. Summary: invoiced, paid, outstanding, overdue. Statement: invoices + payments + running balance, paginated. |
| **D5** | `AP_PAYMENT_CREATE` and `AP_READ` for Super Administrator, Store Manager, Accountant. |
| **D6** | No idempotency. Canonical path is `/accounts-payable/payments`. |

Audit: `SUPPLIER_PAYMENT_CREATED` on `SupplierPayment` in the same TX as the payable update.

## Out

POS `/payments/*`, allocations, overpayment workflow, RPT-005, V1–V29 edits.
