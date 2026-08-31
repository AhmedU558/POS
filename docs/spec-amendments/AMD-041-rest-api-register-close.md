# AMD-041 — Register close and Z report

**Target:** POS REST API Specification §18
**Status:** **Approved** — Story 7.4

`POST /register-sessions/{id}/close` (`REGISTER_CLOSE`) body `{ actualCash, notes? }`.
`GET /register-sessions/{id}/closing-report` (`REGISTER_READ`, `REGISTER_OPEN`, or `REGISTER_CLOSE`).
Close is one business transaction: session lock, totals, Z-report metadata, audit.
