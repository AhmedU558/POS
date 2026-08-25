# ADR-009: Two health surfaces with distinct audiences

**Status:** Accepted — Phase 0
**Date:** 2026-08-25

## Context

The foundation exposed a hand-written `GET /api/health` while `spring-boot-starter-actuator` was
also on the classpath. The two overlapped, and neither matched the approved contract:

- `/api/health` sat outside the `/api/v1` base path required by REST API Specification section 2.1,
  appears in none of that document's endpoint tables, and returned a bare `{"status":"UP"}` rather
  than the standard envelope from section 5.1.
- `/actuator/health` existed but the security configuration did not permit it, so it answered 401
  and was useless as a container probe.

System Architecture Document section 21 requires health endpoints, and section 23 describes a
containerised deployment that needs a liveness probe.

## Decision

Keep both, with separate audiences and non-overlapping responsibilities.

| Surface | Audience | Shape |
|---------|----------|-------|
| `GET /api/v1/health` | API clients | Standard success envelope (API spec section 5.1) |
| `GET /actuator/health` | Container orchestration and load balancers | Actuator's own format |

Actuator exposure is restricted to `health` and `show-details` is `never`, so no dependency
topology, version, or configuration detail is published to an unauthenticated caller.

Both are permitted without authentication. Neither reveals anything beyond liveness.

## Consequences

- The unversioned `/api/health` path is gone; a regression test asserts it is no longer served
  as an anonymous surface.
- Adding a new Actuator endpoint requires an explicit exposure change, which is a reviewable diff.
