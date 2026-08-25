# Agent instructions

Conventions for AI agents working in this repository. Human contributors should read
[README.md](README.md) first.

## Source of truth

The approved specifications in `Documents/` are binding:

| Document | Owns |
|---|---|
| `POS_Management_System_PRD.txt` | Product scope |
| `POS_Management_System_SRS.txt` | Functional and non-functional requirements |
| `POS_System_Architecture_Document_SAD.txt` | Architecture, modules, security model |
| `POS_Database_Design_and_ERD_Specification.txt` | Schema, naming, constraints, indexes |
| `POS_REST_API_Specification.txt` | Endpoints, DTOs, envelopes, error codes, status codes |
| `POS_UI_UX_and_Screen_Architecture_Specification.txt` | Screens, tokens, interaction, accessibility |
| `POS_Implementation_and_Development_Plan.txt` | Phases, standards, Definition of Done |

`_bmad-output/specs/spec-pos-system/` holds the distilled SPEC kernel. Its frontmatter cites only
the PRD and SRS, so it does **not** carry the architectural, schema, API or UI constraints. Read
the specification documents directly for those.

Do not invent a requirement the documentation already defines, and do not silently resolve a
contradiction between two approved documents — record it in `docs/adr/` instead.

## Do not

- Add Tailwind CSS, shadcn/ui, or a component library. See
  [ADR-011](docs/adr/ADR-011-21st-dev-usage.md).
- Add an `organizations` table. `stores` is the root organisational entity. See
  [ADR-010](docs/adr/ADR-010-organization-tier.md).
- Commit secrets, or reintroduce hardcoded fallbacks such as `${JWT_SECRET:some-literal}`.
- Use `float` or `double` for money. `BigDecimal` in Java, `NUMERIC` in PostgreSQL.
- Expose JPA entities from controllers. Requests and responses use DTOs.
- Edit a migration that has already been applied. Add a new versioned file.
- Change `spring.flyway.baseline-on-migrate` to `true`.
- Return an ad-hoc response body. Success uses `ApiResponse`, failure uses `ApiException` plus a
  documented `ErrorCode`.

## Backend conventions

- Package per business module under `com.pos`, following SAD section 27. Cross-cutting code lives
  in `com.pos.common.{config,exception,response,security,validation}`.
- Constructor injection only. No field injection, no Lombok in new code.
- `@Transactional` boundaries belong in application services, never in controllers or repositories.
- Validate requests with Jakarta Bean Validation and `@Valid`.
- Authorisation is enforced server-side. `@EnableMethodSecurity` is active; use permission codes
  from REST API Specification section 4.3, not role-name checks.
- Every store-scoped resource must be checked against the caller's permitted stores
  (REST API Specification section 30).

## Frontend conventions

- Directory structure follows UI/UX Specification section 35 (`components/`, `features/`, `lib/`,
  `hooks/`, `services/`, `types/`, `styles/`).
- Styling uses the design tokens in `src/styles/tokens.css`. Add a token rather than a magic value;
  the token set derives from UI/UX Specification sections 7.2 and 7.3.
- Colour is never the only signal for status — pair it with an icon or label (spec section 7.1).
- Focus states stay visible, and POS flows must work from the keyboard and a barcode scanner
  (spec sections 29 and 31).
- The frontend never computes authoritative money, expected cash, or variance. It displays what
  the API returns (spec sections 11.3 and 14).

## Testing

- Backend integration tests extend `AbstractIntegrationTest`, which supplies a PostgreSQL
  container through `@ServiceConnection`. Do not point tests at a static shared database.
- Test behaviour, not wiring. A test that only asserts something rendered is not coverage.
- Never report a suite as passing without running it.

## Phase discipline

The Implementation Plan sequences the work (section 9). Do not implement a later phase's
functionality while completing an earlier one. Authentication and RBAC are Phase 1.
