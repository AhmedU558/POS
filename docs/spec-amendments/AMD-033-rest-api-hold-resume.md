# AMD-033 — Hold and resume sale

**Target:** POS REST API Specification §16
**Status:** **Approved** — Story 6.4

Empty `payments` on `POST /sales` creates status HELD.
`POST /sales/{id}/hold` and `POST /sales/{id}/resume` use `SALE_CREATE`. Resume body: `{ registerSessionId, payments[] }`.
