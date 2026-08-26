---
title: 'Goal B — Password rotation enforcement'
type: 'feature'
created: '2026-08-26'
status: 'awaiting-approval'
baseline_commit: 'a602813'
review_loop_iteration: 1
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/spec-amendments/AMD-002-rest-api-self-service-password-change.md'
  - '{project-root}/docs/adr/ADR-013-forced-initial-password-rotation.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Goal A flags the first administrator `is_password_change_required`, but the flag is
inert: nothing reads it and no endpoint lets a holder satisfy it. Shipping login (Story 1.4) in
that state would let the bootstrap administrator authenticate indefinitely with the
operator-supplied password — the hole rotation exists to close.

**Approach:** Enforce the flag server-side from authoritative database state, and provide the one
operation that clears it. Both are specified by the approved AMD-002.

## Boundaries & Constraints

**Always:**
- Enforcement reads the database on each request, never a token claim or anything client-supplied.
- The current password is re-verified even though the caller is authenticated.
- The hash and the flag change together, in one operation, inside one transaction.
- A failed change leaves the account exactly as it was.
- `SecurityConfig` is **tightened**, never loosened: only login, forgot-password and reset-password
  stay public, per API §4.2.
- Audit goes through the existing `AuditRecorder` with a human actor.
- All 141 existing tests stay green.

**Ask First:**
- Adding anything to the allow-list beyond the approved three.
- Any composition rule beyond the approved 12-character minimum.
- Any change to `User.changePassword`'s single-operation invariant.

**Never:**
- JWT, login, refresh tokens, `UserDetailsService` — Story 1.4.
- An unauthenticated setup endpoint, or any API outside the approved contract.
- A distinct error code for a wrong current password (it would be a password oracle).
- Modifying V1, V2 or V3.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error Handling |
|---|---|---|---|
| Valid change | Correct current, new ≥ 12, different | **204**, flag cleared, audit row | N/A |
| Wrong current password | Authenticated, wrong current | **401** `AUTHENTICATION_REQUIRED` | Account unchanged |
| New too short | Correct current, new < 12 | **422** `BUSINESS_RULE_VIOLATION` | Account unchanged |
| New equals current | Correct current, new == current | **422** `BUSINESS_RULE_VIOLATION` | Account unchanged |
| Missing field | Blank or absent `currentPassword` | **400** `VALIDATION_ERROR` | Account unchanged |
| Unauthenticated | No principal | **401** | Endpoint requires authentication |
| Rotation pending, protected route | Flagged user calls `/api/v1/products` | **403** `PASSWORD_CHANGE_REQUIRED` | Blocked by filter |
| Rotation pending, allow-listed | Flagged user calls `/auth/me` | Passes the filter | N/A |
| Rotation not pending | Unflagged user, protected route | Passes | N/A |
| Client-supplied flag | Body carries `isPasswordChangeRequired:false` | Ignored entirely | Database is authoritative |
| Public route | Anonymous `/api/v1/health` | Unaffected | Filter no-ops |
| After a successful change | Same user retries protected route | Now permitted | Flag cleared |
| Audit | Successful change | Human-actor audit row exists | Joins the same transaction |
| Rollback | Persistence or audit fails | Password **and** flag unchanged | One transaction |
| Concurrent changes | Two requests, same current password | Consistent final state | Last-write-wins, documented |

</frozen-after-approval>

## Code Map

- `AMD-002` is the approved contract — endpoint, statuses, error mapping, allow-list, policy.
  Read it; do not re-derive it.
- `common/response/ErrorCode.java` — add `PASSWORD_CHANGE_REQUIRED` (403). **This breaks
  `GlobalExceptionHandlerTests.theCatalogueMatchesTheSpecifiedErrorCodes`**, which asserts exactly
  the 15 existing codes. That test is doing its job; add the code to it.
- `common/security/SecurityConfig.java:77` — the blanket `/api/v1/auth/**` permitAll to narrow.
  `RestAccessDeniedHandler` shows how to write an envelope from outside the controller layer.
- `users/domain/User.java` — `changePassword(hash)` sets hash and clears flag in one operation;
  `isPasswordChangeRequired()` is the authoritative read.
- `audit/service/AuditRecorder.java` — `MANDATORY`, so callers must be transactional;
  `AuditActor.user(id)` for a human actor.
- `com/pos/auth/` — empty on disk. Sub-packages `controller`, `dto`, `service`, `security`.
- `SecurityFoundationTests` asserts today's `/api/v1/auth/**` behaviour and may need updating.

**Filter placement:** before `AuthorizationFilter`, so the check runs after authentication has been
established but before the permission decision — the position ADR-013 describes.

## Tasks & Acceptance

**Execution:**
- [x] `common/response/ErrorCode.java` — add `PASSWORD_CHANGE_REQUIRED` (403) per AMD-002 §3.
- [x] `auth/dto/ChangePasswordRequest.java` — `@NotBlank` both fields. Length is a policy rule in
  the service, not a field constraint: AMD-002 maps policy failures to 422, not 400.
- [x] `auth/service/PasswordChangeService.java` — `@Transactional`; current password first, then
  policy, then change and audit. A wrong current password must reveal nothing about policy.
- [x] `auth/controller/AuthController.java` — the endpoint; principal from `Authentication`.
- [x] `auth/security/PasswordRotationFilter.java` — blocks non-allow-listed routes while flagged.
- [x] `common/security/SecurityConfig.java` — narrow the permitAll; register the filter.
- [x] `test/java/com/pos/auth/ChangePasswordApiTests.java` — the contract: every status and code.
- [x] `test/java/com/pos/auth/PasswordRotationEnforcementTests.java` — the filter, the allow-list,
  and that client-supplied state changes nothing.
- [x] `test/java/com/pos/auth/PasswordChangeTransactionTests.java` — rollback and concurrency.
- [x] `test/.../GlobalExceptionHandlerTests.java` — add the new code to the catalogue assertion.

**Acceptance Criteria:**
- Given a flagged account, when it calls any route outside the allow-list, then 403
  `PASSWORD_CHANGE_REQUIRED` — and the same account reaches all three allow-listed routes.
- Given a wrong current password, when a change is attempted, then 401 and the stored hash and flag
  are both unchanged.
- Given a successful change, then the new password verifies, the flag is clear, an audit row with a
  human actor exists, and the account can then reach protected routes.
- Given a request body carrying a rotation flag, then it is ignored entirely.
- `mvn -B clean verify` keeps all 141 prior tests passing.

## Spec Change Log

**Added during adversarial-review triage** (all approved-scope, none contradicting AMD-002):

- `PasswordRotationFilter` fails **closed** on an authenticated principal that resolves to no row.
  Originally `.orElse(false)`. All three reviewers flagged it independently: Story 1.4 chooses the
  token subject, and any mismatch (email, user id, different casing) would have silently disabled
  enforcement system-wide with every test green.
- Allow-list matched against the path **within the application**, not the context-path-inclusive
  request URI. Setting `server.servlet.context-path` would otherwise lock every flagged account out
  of the endpoint that clears its own flag.
- Repository failure inside the filter is caught and answered with the standard envelope, failing
  closed. The filter sits after `ExceptionTranslationFilter`, so a database blip would otherwise
  escape as the container default error page.
- Maximum password length of 72 **bytes**. BCrypt truncates silently beyond that; accepting longer
  input protects a long passphrase with a fraction of itself and makes the "must differ" check
  compare truncated forms. Not a composition rule — the algorithm's own bound.
- Minimum length counted in **code points**, not `String.length()`. Six emoji are twelve UTF-16
  units and would otherwise have satisfied a twelve-character policy.
- A **deactivated** account cannot change its password. Reported as a credential failure so the
  endpoint does not disclose account status.
- 405 and 415 map to 400 `VALIDATION_ERROR`. The catch-all `@ExceptionHandler(Exception.class)`
  outranks Spring's own resolvers, so both previously returned 500 `INTERNAL_ERROR`. This endpoint
  is the first body-carrying one in the system, which is what made it reachable.
- Audit rows carry `ip_address` and `user_agent` via the existing `AuditRequestContext`. Database
  Design section 20.1 records both "when available"; this is the first audited action with a human
  at the other end.
- Failed current-password verification logs at WARN. The credential is never logged.
- `FilterRegistrationBean(enabled=false)` suppresses Boot's servlet auto-registration, making the
  security chain the filter's only home.

**Not changed, deliberately** — raised for human decision instead:

- 401 for a wrong current password. A reviewer argued for 422, noting SPA interceptors treat 401 as
  "token dead" and will log the user out of the only session permitted to clear the flag. That is a
  real usability problem, but 401 is what AMD-002 section 2 specifies and what was approved. Not
  changed without change control.

## Design Notes

**A database read per request** is ADR-013's choice: flagging an account then takes effect on its
next request rather than at token expiry. One indexed lookup, on a request already loading the
principal.

**The current password is re-verified** because authentication proves the session, not the person.
A stolen token must not be enough to seize the account permanently.

**Concurrency is last-write-wins** — optimistic locking is deferred. The invariant that matters,
that the flag is never cleared without a password being set, holds either way: they are one
operation.

## Verification

**Commands:**
- `cd backend && mvn -B clean verify` — `BUILD SUCCESS`, 141 prior plus new, 0 failures.
- Mutation checks, each reverted under a checksum assertion: remove the filter; empty its block;
  drop current-password verification; clear the flag before persisting; change the password without
  clearing; drop the length check; store the raw password; widen the allow-list; wrong status.
- `git status --porcelain` — no secrets, no build output, V1/V2/V3 unmodified.
