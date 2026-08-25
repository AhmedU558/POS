# POS Management System

Integrated POS, Inventory & Business Management System — a modular Spring Boot monolith with a
Next.js frontend, backed by PostgreSQL and Redis.

The approved specifications in [`Documents/`](Documents/) are the source of truth. Where this
README and a specification disagree, the specification wins.

---

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21 LTS, Spring Boot 3.2, Maven |
| Persistence | PostgreSQL 15, Spring Data JPA / Hibernate, Flyway |
| Cache | Redis 7 |
| Security | Spring Security (JWT from Phase 1) |
| API docs | OpenAPI / Swagger UI (springdoc) |
| Frontend | Next.js 16, React 19, TypeScript, CSS custom properties |
| Backend tests | JUnit 5, Spring Test, Mockito, Testcontainers |
| Frontend tests | Vitest, React Testing Library |

## Repository layout

```
backend/     Spring Boot API (modular monolith)
frontend/    Next.js POS and admin client
database/    Reserved for seed/reference data (schema migrations live on the backend classpath)
docs/adr/    Architecture decision records ADR-008 onward
infra/       Local and deployment infrastructure
Documents/   Approved specifications (PRD, SRS, SAD, Database, API, UI/UX, Plan)
_bmad/       BMad methodology installation
_bmad-output/ BMad artefacts (SPEC kernel and companions)
```

---

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 22+
- Docker (required for local services **and** for the backend integration tests)

## First-time setup

```bash
cp .env.example .env
```

Fill in `.env`. `POSTGRES_PASSWORD` and `DATABASE_PASSWORD` are required — the API refuses to
start without a database password, because credentials must never be committed to source control
(SAD section 15, Database Design section 28).

Generate the Phase 1 authentication secrets with a real generator, never by hand:

```bash
openssl rand -hex 32
```

## Start the local environment

**1. Backing services**

```bash
docker compose up -d
```

Both containers expose health checks and bind to `127.0.0.1` only. Confirm they are healthy:

```bash
docker compose ps
```

**2. Backend**

```bash
cd backend && mvn spring-boot:run
```

Environment variables are read from the shell, not from `.env`. Export them first, for example:

```bash
set -a && . ./.env && set +a
```

| URL | Purpose |
|---|---|
| `http://localhost:8080/api/v1/health` | API health, standard response envelope |
| `http://localhost:8080/actuator/health` | Infrastructure liveness probe |
| `http://localhost:8080/swagger-ui.html` | OpenAPI explorer (set `SPRINGDOC_ENABLED=false` in production) |

**3. Frontend**

```bash
cd frontend && npm install && npm run dev
```

Serves on `http://localhost:3000`, which is the default allowed CORS origin.

---

## Tests

**Backend** — unit tests plus Testcontainers integration tests. Docker must be running; the suite
starts its own disposable PostgreSQL and never touches the compose database.

```bash
cd backend && mvn verify
```

If Testcontainers reports `Could not find a valid Docker environment` on an older daemon, override
the pinned Docker API version — see [ADR-012](docs/adr/ADR-012-testcontainers-docker-api-version.md):

```bash
mvn verify -Ddocker.api.version=1.41
```

**Frontend**

```bash
cd frontend && npm test
```

```bash
cd frontend && npx tsc --noEmit && npm run lint && npm run build
```

---

## Database migrations

Flyway migrations live at `backend/src/main/resources/db/migration` and run automatically on
startup. See [ADR-008](docs/adr/ADR-008-migration-location.md).

`baseline-on-migrate` is deliberately `false`: enabling it against a non-empty database causes
Flyway to baseline and silently skip pending migrations.

Every schema change needs a new `V<n>__<description>.sql` file. Applied migrations are never
edited.

---

## Contributing

- Branches follow Implementation Plan section 5: `main` is production-ready; feature branches are
  short-lived and focused.
- Every change traces to an approved requirement and satisfies the Definition of Done in
  Implementation Plan section 33.
- Work is driven through BMad. Start with `bmad-help` if you are unsure which skill applies.
- Agent-specific conventions live in [`AGENTS.md`](AGENTS.md).
