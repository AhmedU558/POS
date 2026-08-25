# ADR-013: Initial-password rotation is enforced by the application

**Status:** Accepted — Phase 1. AMD-001 and AMD-002 approved 2026-08-25.
**Date:** 2026-08-25
**Depends on:** [AMD-001](../spec-amendments/AMD-001-database-design-rotation-and-bootstrap.md),
[AMD-002](../spec-amendments/AMD-002-rest-api-self-service-password-change.md)

## Context

The first administrator is created from a credential supplied by an operator
([ADR-015](ADR-015-first-administrator-provisioning.md)). That credential passes through a
deployment pipeline, a secret store, possibly a shell history, and at least one human. It is
compromised by construction and must not remain valid.

Two ways to guarantee it is replaced:

1. **Operational runbook** — document "rotate immediately after bootstrap" and rely on the operator.
2. **Application enforcement** — the account cannot be used for anything until its password changes.

The pre-mortem run during the provisioning analysis rated "the operator will remove or rotate the
bootstrap credential" at *very low* confidence with *high* impact. A control that depends on
someone remembering, under deployment-day pressure, is not a control.

## Decision

The application enforces rotation. An account carrying a credential it did not choose is marked,
and while that mark is set the account may reach only the operations needed to clear it.

Three properties make this an enforcement rather than a suggestion:

- **State lives in the database, not in a token.** A token claim is a snapshot; a database read is
  authoritative. Marking an account for rotation takes effect on its next request, not at token
  expiry.
- **Enforcement sits in the authorization layer, after authentication.** Rotation is not an
  authentication outcome. Failing login would strand the holder with no route to fix their own
  account; succeeding and then constraining what the session may do keeps the two concerns separate
  and leaves exactly one path open.
- **The client is told, but not trusted.** The login response carries a usability signal so the UI
  can navigate straight to the right screen. Forging or dropping that signal changes nothing —
  REST API Specification §26 makes server-side validation authoritative.

The allow-list while rotation is pending is `/auth/change-password`, `/auth/logout` and
`/auth/me`. Everything else returns 403 `PASSWORD_CHANGE_REQUIRED`.

## Consequences

- Requires both amendments. AMD-001 adds the state; AMD-002 adds the endpoint that satisfies it.
  **Approving rotation without AMD-002 produces a system that demands a password change while
  offering no way to make one** — a permanent lockout of the first administrator.
- The mechanism generalises beyond bootstrap: any administrator-issued temporary password can use
  the same flag, which is the behaviour AUTH-005 implies when an administrator manages an account.
- One authorization rule evaluated per request. Negligible, because the authenticated user and
  their permissions are already loaded to make a permission decision.
- A forced-rotation screen is not in the approved UI/UX specification. Raised in AMD-002; owned by
  Story 1.10.

## Alternatives rejected

- **Runbook-only rotation.** Explicitly rejected by the product owner, and the pre-mortem's
  lowest-confidence assumption.
- **Rejecting login outright while rotation is pending.** Leaves no route to comply.
- **Expiring the credential after a time window.** Adds a clock dependency and still permits a
  window of full-privilege use with a compromised credential.
