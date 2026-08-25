# Architecture Decision Records

The approved System Architecture Document records ADR-001 through ADR-007 in its section 28.
Numbering continues from ADR-008 here so the two sets never collide.

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
