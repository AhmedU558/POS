# ADR-027: Story 5.3 supplier invoices

**Status:** Accepted — Phase 5, Story 5.3
**Date:** 2026-08-31

## Decision

D1–D6 as approved: independent `supplier_invoices`; `remaining_amount` computed;
PATCH only while OPEN; no store scope; `AP_READ`/`AP_WRITE` for Super Admin,
Accountant, Store Manager; audit `SUPPLIER_INVOICE_CREATED` / `UPDATED`.

## Out

Payments, overdue/summary APIs, statements, allocations. V1–V27 unchanged.
