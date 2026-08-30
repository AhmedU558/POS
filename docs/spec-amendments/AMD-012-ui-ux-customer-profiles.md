# AMD-012 — Customer list and profile screens

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** §18, screen inventory SCR-015 / SCR-016, §36
**Status:** **Approved** — Story 4.1 / D1–D8
**Date:** 2026-08-31
**Companion:** [ADR-021](../adr/ADR-021-story-4-1-customer-profiles.md),
[AMD-011](AMD-011-rest-api-customer-profiles.md)

## 1. The gap

SCR-015 and SCR-016 are MUST. §18 also names purchase history, store credit,
statement, and POS quick-create, which belong to later stories.

## 2. Approved Phase 4 slice

| Screen | Route | Permission |
|--------|-------|------------|
| SCR-015 Customers | `/customers` | `CUSTOMER_READ`; create control needs `CUSTOMER_WRITE` |
| SCR-016 Customer Profile | `/customers/[id]` | `CUSTOMER_READ`; save needs `CUSTOMER_WRITE` |
| Create (profile form) | `/customers/new` | `CUSTOMER_WRITE` |

SCR-015: search (`query`), status filter (`isActive`), paginated table of code,
name, phone, email, credit limit, labelled status (not colour alone).

SCR-016: contact fields and status from AMD-011. No sales history, statement,
or credit ledger.

Navigation to Customers is shown when the user has `CUSTOMER_READ`.

## 3. Out of this amendment

POS F4 customer selector, checkout create, SCR-017, statements, purchase history.
