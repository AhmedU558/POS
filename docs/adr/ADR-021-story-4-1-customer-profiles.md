# ADR-021: Story 4.1 customer profiles

**Status:** Accepted — Phase 4, Story 4.1
**Date:** 2026-08-31
**Depends on:** [AMD-011](../spec-amendments/AMD-011-rest-api-customer-profiles.md),
[AMD-012](../spec-amendments/AMD-012-ui-ux-customer-profiles.md)

## Context

Implementation Plan Phase 4 starts with customer profiles (CUS-001). The approved
documents name `customers` columns, four CRUD endpoints, SCR-015/SCR-016, and
`CUSTOMER_READ` / `CUSTOMER_WRITE`, but leave store-scope, DTOs, role grants,
audit, and later-phase endpoints unresolved. D1–D8 were approved as recorded
below.

## Decision

| ID | Decision |
|----|----------|
| **D1** | Customers are shared master data (same class as catalog products). No `store_id`. No `storeId` query param. No `StoreScopeEvaluator`. Authorization is permission-only. |
| **D2** | `customers` columns are only: `id`, `customer_code` (unique), `name`, `phone`, `email`, `address`, `credit_limit` (`NUMERIC(19,4)` ≥ 0), `is_active`, `created_at`, `updated_at`. Constraint `uk_customers_customer_code`. No `customer_type`. |
| **D3** | `GET /customers/{id}/sales` and purchase-history UI are omitted until Phase 6 (`sales` does not exist). |
| **D4** | `GET /customers/{id}/statement` and statement UI are omitted (CUS-006 SHOULD; needs credit/sales). |
| **D5** | No `customer_type` column or field (PRD mention is not in the DB/SRS ID list). |
| **D6** | CRUD contract is AMD-011. List filters: `query`, `isActive`, pageable. Unknown later-phase routes are not added. |
| **D7** | Seed `CUSTOMER_READ` and `CUSTOMER_WRITE` for Super Administrator, Store Manager, and Cashier. Not Inventory Manager. Not Accountant. |
| **D8** | Create and update are audited as `CUSTOMER_CREATED` and `CUSTOMER_UPDATED` on `Customer` in the same write transaction. Lists are reads. |

## Consequences

- V18 creates `customers`. V19 seeds the two permissions.
- Credit tables, credit APIs, SCR-017, suppliers, POS checkout create, and
  CUS-003 stay out of this story.

## What this ADR does not change

Stories 3.1–3.4 inventory contracts, applied migrations, and catalog store-scope
behaviour remain as previously decided.
