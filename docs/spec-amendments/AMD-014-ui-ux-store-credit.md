# AMD-014 — Store credit screen

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** §18, screen inventory SCR-017, §36
**Status:** **Approved** — Story 4.2 / D1–D8
**Date:** 2026-08-31
**Companion:** [ADR-022](../adr/ADR-022-story-4-2-store-credit-ledger.md),
[AMD-013](AMD-013-rest-api-store-credit.md)

## 1. The gap

SCR-017 is MUST. §18 names store-credit balance and ledger together with
statements and POS checkout create, which belong to later stories.

## 2. Approved Phase 4 slice

| Screen | Route | Permission |
|--------|-------|------------|
| SCR-017 Store Credit | `/customers/[id]/credit` | `CREDIT_READ`; issue/redeem/adjust needs `CREDIT_WRITE` |

SCR-017 shows customer identity, profile `creditLimit` (labelled, not
enforced), current balance with currency and account status, and a ledger
table (type, signed amount, balance after, when). Colour is not the only
status signal.

`CREDIT_WRITE` reveals the issue/redeem/adjust form (`transactionType`,
`amount`, `currencyCode` when the account does not exist yet).

SCR-016 may show a **Store Credit** control when the user has `CREDIT_READ`.
The profile form itself still has no ledger (AMD-012).

## 3. Out of this amendment

Statements, purchase history, POS F4 / checkout create, customer-specific
pricing.
