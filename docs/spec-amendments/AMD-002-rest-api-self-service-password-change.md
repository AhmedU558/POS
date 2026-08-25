# AMD-002 — Self-service password change

**Target document:** POS REST API Specification v1.0
**Sections affected:** §4.2 (Authentication Endpoints), §28 (Error Codes), §4.1 (login response)
**Status:** **Approved** — 2026-08-25 by product owner
**Raised by:** Phase 1 authentication foundation, Story 1.2 pre-implementation
**Date:** 2026-08-25
**Companion:** [AMD-001](AMD-001-database-design-rotation-and-bootstrap.md) — approve together

---

## 1. The gap

§4.2 enumerates the authentication endpoints:

| Method | Endpoint | Purpose | Permission |
|--------|----------|---------|------------|
| POST | `/auth/login` | Authenticate user | Public |
| POST | `/auth/refresh` | Issue a new access token | Authenticated refresh token |
| POST | `/auth/logout` | Invalidate session/refresh token | Authenticated |
| POST | `/auth/forgot-password` | Start password recovery | Public |
| POST | `/auth/reset-password` | Set new password using recovery token | Recovery token |
| GET | `/auth/me` | Return current authenticated user | Authenticated |

§7 adds `PATCH /users/{id}`, gated on `USER_WRITE`.

**No endpoint lets an authenticated user change their own password.** The two candidates do not fit:

- `/auth/reset-password` requires a *recovery token*, which is the forgotten-password flow. A user
  who knows their password and simply must rotate it would have to trigger a recovery email to
  themselves, turning a routine action into an account-recovery event and inflating the blast
  radius of the recovery flow.
- `PATCH /users/{id}` requires `USER_WRITE`. Per the Story 1.1 seed, only Super Administrator holds
  it. A Cashier issued a temporary password could never rotate it. It is also an administrative
  user-management operation, not self-service, and §7 does not define a password field on it.

This is a genuine hole in the approved contract, not an implementation inconvenience. It blocks
requirement 11 (enforced initial-password rotation): the system would demand a password change
while providing no means to perform one, permanently locking out the first administrator.

---

## 2. Proposed change A — new endpoint

Add to §4.2:

| Method | Endpoint | Purpose | Permission |
|--------|----------|---------|------------|
| POST | `/auth/change-password` | Change own password | Authenticated |

**Request**

```json
{
  "currentPassword": "…",
  "newPassword": "…"
}
```

**Response:** `204 No Content`.

Deliberately no body. Returning the user, a token, or any account detail would invite the response
to be treated as an authentication result. The client re-reads `/auth/me` if it needs fresh state.

**Behaviour**

- The current password is re-verified even though the caller is already authenticated. A stolen
  access token must not be enough to seize an account by changing its password.
- Success clears `is_password_change_required` (AMD-001) and produces an audit record.
- The endpoint remains reachable while a rotation is outstanding — it is the one operation that
  must work in that state.

**Errors** (all in the standard §5.2 envelope)

| Condition | Status | Code |
|-----------|--------|------|
| Missing or malformed fields | 400 | `VALIDATION_ERROR` |
| No/invalid access token | 401 | `AUTHENTICATION_REQUIRED` |
| Current password incorrect | 401 | `AUTHENTICATION_REQUIRED` |
| New password equals current, or fails policy | 422 | `BUSINESS_RULE_VIOLATION` |

An incorrect current password returns `AUTHENTICATION_REQUIRED` rather than a distinct code, so the
endpoint cannot be used as a password oracle against a hijacked session.

---

## 3. Proposed change B — new error code

Add to §28:

| Code | Meaning |
|------|---------|
| `PASSWORD_CHANGE_REQUIRED` | The account must change its password before this operation is permitted |

Returned with **403** while `is_password_change_required` is true, for every endpoint except
`/auth/change-password`, `/auth/logout` and `/auth/me`.

Reusing the existing `ACCESS_DENIED` was considered and rejected: the client cannot distinguish
"you lack permission" from "you must rotate your credential", so it cannot route the user to the
screen that resolves the block. UI/UX §28 requires a permission-denied state to explain the
restriction, and §9.2 requires auto-logout and re-authentication to be communicated clearly. A
generic 403 satisfies neither.

`/auth/logout` and `/auth/me` stay reachable so a blocked session can still end cleanly and the
client can render who is signed in.

---

## 4. Proposed change C — additive login-response field

`POST /auth/login` gains one field indicating that a password change is outstanding.

This is a **backward-compatible addition**, which §2.3 already permits within the current major
version, so it arguably needs no amendment at all. It is recorded here for traceability.

**This field is a usability signal only.** It lets the client navigate straight to the change-password
screen instead of discovering the block by hitting a 403. Enforcement is entirely server-side, per
§26: *"Server-side validation is authoritative; frontend validation is only a usability layer."*
Removing or forging the field changes nothing about what the account can do.

---

## 5. What this amendment does NOT change

- No existing endpoint, path, method, permission, request model or response model is altered.
- No existing error code changes meaning or status.
- `/auth/forgot-password` and `/auth/reset-password` keep their current recovery semantics; this
  amendment does not touch AUTH-006.
- No unauthenticated endpoint is introduced. `/auth/change-password` requires a valid access token,
  honouring requirement 13.
- API versioning is unaffected: all changes are additive within `/api/v1` per §2.3.

---

## 6. Impact analysis

**Authentication flow.** Login is unchanged. A pending rotation does not fail authentication; it
constrains authorization afterwards.

**Authorization layer.** Gains one rule evaluated after authentication and before permission
checks: if rotation is pending and the request is not on the allow-list, return 403
`PASSWORD_CHANGE_REQUIRED`. Enforced from authoritative user state, never from a token claim, so
revoking or granting the state takes effect on the next request rather than at token expiry.

**Rate limiting.** §29 requires authentication endpoints to be rate-limited.
`/auth/change-password` verifies a password and therefore belongs in that set. Rate limiting is
Phase 11 work (Plan §21); this records the requirement so it is not missed.

**OpenAPI.** The endpoint, its error responses, and the new code are documented per §32.

**Frontend.** UI/UX §9 defines the login screen but no change-password screen, and §8's screen
inventory has no entry for one. A forced-rotation screen is therefore also unspecified. This is
flagged, not resolved here — it belongs to the UI/UX specification and to Story 1.10.

**Tests.** Contract tests for the endpoint and each error condition; security tests that a blocked
account is refused everywhere except the allow-list, that the allow-list itself works, and that a
wrong current password does not reveal whether it was wrong for a different reason; a test that the
flag clears exactly once.

---

## 7. Approval record

Approved by the product owner on 2026-08-25, with these resolutions:

1. Change A approved — `POST /auth/change-password`, returning `204 No Content`.
2. Change B approved — error code `PASSWORD_CHANGE_REQUIRED`.
3. Change C approved — the additive login-response field is kept.
4. **Allow-list is exactly three endpoints** while rotation is pending:
   `/auth/change-password`, `/auth/logout`, `/auth/me`. Nothing may be added without first
   presenting the security and functional reason.
5. **Password policy approved: minimum 12 characters, no composition requirements.** No further
   composition rules are to be added unless a future approved requirement explicitly demands them.

**Implementation may proceed.** These land in Stories 1.2 and 1.4-1.6, not Story 1.3.

### Still open

The forced-rotation screen remains absent from UI/UX Specification §8's screen inventory. Owned by
Story 1.10; not resolved by this amendment.
