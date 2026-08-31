# AMD-043 — Current register session lookup

**Target:** POS REST API Specification §18
**Status:** **Approved** — Product UX completion pass
**Raised by:** POS checkout could not survive a page reload

---

## 1. Why this amendment is needed

A sale requires `registerSessionId` (§17, `POST /sales`). That identifier is issued exactly once,
in the response to `POST /registers/{id}/sessions/open`, and is not retrievable afterwards from
any endpoint that does not already know it.

The consequence is a dead end on the shop floor. A cashier opens their till, reloads the browser
or moves to another screen, and the identifier is gone. They cannot resume selling, and they
cannot reopen the register either, because §18 permits only one `OPEN` session per register — the
retry is rejected with `BUSINESS_RULE_VIOLATION`. The drawer is open and unusable until someone
queries the database.

Storing the identifier in browser storage does not fix this: it is lost on a different device, a
cleared cache, or a shift handover, and it can go stale after the session is closed elsewhere.
The server holds the authoritative answer and needs to be able to give it.

---

## 2. Amendment

Add to §18:

`GET /register-sessions/current` — the open session belonging to the authenticated cashier.

- **Permission:** `REGISTER_READ`, `REGISTER_OPEN` or `SALE_CREATE`.
- **Response:** `200` with `data` set to the `RegisterSessionResponse` already defined by
  AMD-035, or `data: null` when the caller has no open session.
- **Scope:** store-scoped like every other session endpoint; a session in a store the caller is
  not assigned to is reported as absent.
- Where a cashier holds open sessions on more than one register, the most recently opened one is
  returned.

Absence is reported as a null payload rather than `404`. Having no open till is the ordinary
state at the start of a shift, and the POS screen branches on it; a `404` would force every
client to treat a normal condition as an error.

No existing endpoint, DTO, permission or status code changes.

---

## 3. Verification

`CurrentRegisterSessionApiIntegrationTests` covers the four behaviours: null when nothing is
open, the caller's own open session, isolation from another cashier's session, and null again
once the session is closed.
