# AMD-037 — Register cash-in and cash-out

**Target:** POS REST API Specification §18
**Status:** **Approved** — Story 7.2

`POST /register-sessions/{id}/cash-in` and `POST /register-sessions/{id}/cash-out` (`REGISTER_CASH`).
Body `{ amount, reason? }`. Open session required. Store-scoped.
