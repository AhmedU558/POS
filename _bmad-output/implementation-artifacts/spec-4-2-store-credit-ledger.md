---
title: 'Story 4.2 — Store Credit Ledger'
type: 'feature'
created: '2026-08-31'
status: 'in-review'
baseline_commit: '27df605'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/ADR-022-story-4-2-store-credit-ledger.md'
  - '{project-root}/docs/spec-amendments/AMD-013-rest-api-store-credit.md'
  - '{project-root}/docs/spec-amendments/AMD-014-ui-ux-store-credit.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** CUS-004 / CUS-005 require issue/redeem store credit and an
immutable ledger. There are no credit tables, no `CREDIT_*` permissions, and
no SCR-017.

**Approach:** Persist `customer_credits` + `customer_credit_transactions`
(ADR-022 D2), expose the two REST endpoints (D3–D5), seed Cashier/Manager/Admin
grants (D6), audit writes (D7), and ship SCR-017 only (D8).

Binding addenda: ADR-022, AMD-013, AMD-014.

## Boundaries & Constraints

**Always:**
- No `store_id`. No `StoreScopeEvaluator`. `CREDIT_READ` / `CREDIT_WRITE` only.
- Ledger immutable. Balance + ledger + audit in one write transaction.
- `creditLimit` is display-only. Balance cannot go below zero.
- DTOs at the controller; `ApiResponse` / `ApiException` + documented `ErrorCode`.

**Never:**
- Statements, sales history, POS tender, suppliers, CUS-003 pricing.
- Editing an already-applied migration (including V18/V19).
- A new `ErrorCode`.

</frozen-after-approval>
