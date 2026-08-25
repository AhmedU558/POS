# ADR-008: Flyway migrations live on the backend classpath

**Status:** Accepted — Phase 0
**Date:** 2026-08-25

## Context

Two approved documents describe where migrations live, and they do not agree literally.

- Implementation & Development Plan section 4 sketches a repository tree containing
  `database/migrations/`.
- System Architecture Document section 13 and Database Design & ERD Specification section 26
  require versioned migrations, "preferably Flyway or Liquibase", but name no filesystem path.

The implementation places the baseline at
`backend/src/main/resources/db/migration/V1__init_schema.sql`, which is the location Spring Boot's
Flyway auto-configuration scans by default.

## Decision

Migrations stay on the backend classpath at `backend/src/main/resources/db/migration`.

The `database/` directory in the Implementation Plan tree is treated as illustrative rather than
normative, on the grounds that:

1. Only the SAD and the Database specification are binding on database mechanics; the
   Implementation Plan tree is a sketch inside a planning document.
2. A classpath location means the migration set is packaged inside the application artifact, so
   the deployed jar and the schema it expects can never drift apart. A sibling `database/`
   directory would have to be copied or mounted separately at deploy time.
3. Database Design section 26 requires migrations to be "applied automatically in development/test
   and controlled in production". Classpath migrations give the automatic half for free.

## Consequences

- The empty top-level `database/` directory is reserved for seed and reference data, which
  Database Design section 26 asks to keep "separated from schema migrations where practical".
- Anyone reading the Implementation Plan tree will find this ADR through `docs/adr/`.
- No approved specification was modified.
