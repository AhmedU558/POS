# Architecture Decision Records

The approved System Architecture Document records ADR-001 through ADR-007 in its section 28.
Numbering continues from ADR-008 here so the two sets never collide.

Where a decision requires an approved specification to change, the change is proposed under
[`docs/spec-amendments/`](../spec-amendments/README.md) and the ADR depends on it. ADRs never
amend a specification by themselves.

These records do not amend the approved specifications. Where a specification is silent or two
specifications appear to disagree, the ADR states which reading was adopted and why, so the
decision is not re-litigated in every story.

| ADR | Decision |
|-----|----------|
| [ADR-008](ADR-008-migration-location.md) | Flyway migrations live on the backend classpath |
| [ADR-009](ADR-009-health-endpoints.md) | Two health surfaces with distinct audiences |
| [ADR-010](ADR-010-organization-tier.md) | `stores` is the root organisational entity |
| [ADR-011](ADR-011-21st-dev-usage.md) | 21st.dev is used for inspiration and review, not installation |
| [ADR-012](ADR-012-testcontainers-docker-api-version.md) | Docker Remote API version is pinned for Testcontainers |
| [ADR-013](ADR-013-forced-initial-password-rotation.md) | Initial-password rotation is enforced by the application |
| [ADR-014](ADR-014-audit-precedes-bootstrap.md) | Audit foundation is built before first-administrator provisioning |
| [ADR-015](ADR-015-first-administrator-provisioning.md) | First administrator is provisioned from operator-supplied secrets at startup |
| [ADR-016](ADR-016-system-actor-convention.md) | A system-initiated action is recorded with a null actor |
| [ADR-017](ADR-017-store-scope-many-to-many.md) | Store scope is `user_stores` plus `StoreScopeEvaluator` |
| [ADR-018](ADR-018-story-3-2-stock-receiving-scope.md) | Story 3.2 is standalone receiving; transfers are deferred |
