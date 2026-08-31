# AMD-035 — Open register session

**Target:** POS REST API Specification §18
**Status:** **Approved** — Story 7.1

`POST /registers/{id}/sessions/open` (`REGISTER_OPEN`) body `{ openingCash }`.
`GET /register-sessions/{id}` (`REGISTER_READ` or `REGISTER_OPEN`).
One OPEN session per register. Store-scoped.
