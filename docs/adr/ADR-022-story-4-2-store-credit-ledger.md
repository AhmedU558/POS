# ADR-022: Story 4.2 store credit ledger

**Status:** Accepted — Phase 4, Story 4.2
**Date:** 2026-08-31
**Depends on:** [AMD-013](../spec-amendments/AMD-013-rest-api-store-credit.md),
[AMD-014](../spec-amendments/AMD-014-ui-ux-store-credit.md),
[ADR-021](ADR-021-story-4-1-customer-profiles.md)

## Context

Implementation Plan Phase 4 story 4.2 is CUS-004 / CUS-005 (issue/redeem store
credit; ledger every change) and SCR-017. The approved documents name the two
tables, two endpoints, and `CREDIT_READ` / `CREDIT_WRITE`, but leave store
scope, DTO bodies, role grants, credit-limit behaviour, and ledger listing
unresolved. D1–D8 below fill those gaps without contradicting the ERD (one
credit account per customer, no `store_id`) or REST §30 (store-scope applies
only to store-scoped resources).

## Decision

| ID | Decision |
|----|----------|
| **D1** | Store credit is customer-scoped master data (ERD `customers` 1:0..1 `customer_credits`). No `store_id`. No `storeId` query param. No `StoreScopeEvaluator`. Authorization is permission-only (`CREDIT_READ` / `CREDIT_WRITE`). |
| **D2** | Tables are only `customer_credits` and `customer_credit_transactions` as AMD-013. Ledger rows are immutable (same trigger pattern as `inventory_transactions`). Cached `balance` is updated in the same transaction as the ledger insert and must stay ≥ 0. |
| **D3** | Endpoints are only `GET /customers/{id}/credit` and `POST /customers/{id}/credit/transactions`. `GET /customers/{id}/statement` and POS sale redemption stay out. GET embeds a pageable ledger so SCR-017 does not need a third route. |
| **D4** | `transactionType` is `ISSUE` / `REDEEM` / `ADJUST`. ISSUE and REDEEM take `amount` > 0 (service posts `+amount` / `−amount`). ADJUST takes a signed nonzero `amount`. Optional `referenceType` / `referenceId` are stored, not resolved (sales do not exist yet). |
| **D5** | `customers.credit_limit` is display-only. No document caps ISSUE at that limit, so it is not enforced. REDEEM/ADJUST that would make `balance` < 0 → 422 `BUSINESS_RULE_VIOLATION`. Inactive customer on write → 409 `RESOURCE_INACTIVE`. First write requires `currencyCode` (ISO-4217, 3 letters); later writes must match the account. No `Idempotency-Key` (REST §6 does not list this route). Concurrent writers lock the account row (`SELECT FOR UPDATE`); first insert uses `ON CONFLICT DO NOTHING` then lock. |
| **D6** | Seed `CREDIT_READ` and `CREDIT_WRITE` for Super Administrator, Store Manager, and Cashier. Not Inventory Manager. Not Accountant. (SCR-017 primary roles plus Super Admin, same grant set as ADR-021 D7.) |
| **D7** | Writes audit `CREDIT_ISSUED` / `CREDIT_REDEEMED` / `CREDIT_ADJUSTED` on entity type `CustomerCreditTransaction` in the same write transaction. GET is a read. |
| **D8** | SCR-017 is `/customers/[id]/credit`. View needs `CREDIT_READ`; issue/redeem/adjust needs `CREDIT_WRITE`. Profile may link here; it must not embed the ledger (AMD-012). |

## Consequences

- V20 creates the two tables. V21 seeds the two permissions.
- Statements, POS store-credit tender, suppliers, and CUS-003 stay out.

## What this ADR does not change

Story 4.1 customer CRUD, V18/V19, and catalog/inventory store-scope behaviour
remain as previously decided.
