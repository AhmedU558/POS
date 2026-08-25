---
title: 'Story 0.1 — Startup validation of required configuration'
type: 'feature'
created: '2026-08-25'
status: 'done'
baseline_commit: '417165d22cfdad4ad5f0ccfe2be1727fc9352734'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Phase 0 removed every hardcoded credential fallback, so `application.yml` declares `spring.datasource.password`, `jwt.secret` and `jwt.refresh-secret` as bare `${ENV_VAR}` placeholders. Spring Boot's binder does not fail on an unresolvable placeholder — it passes the literal through. A missing `DATABASE_PASSWORD` therefore surfaces as `FATAL: password authentication failed for user "postgres"`, pointing at the database rather than the unset variable, and missing JWT secrets go undetected because no bean binds them yet.

**Approach:** Validate required configuration once, at the earliest point the resolved `Environment` exists and before any `DataSource` is built, aborting startup with one aggregated message that names the offending environment variables and the remedy.

## Boundaries & Constraints

**Always:**
- Runs before `DataSource` creation, so credential problems are caught here, not by Postgres.
- One aggregated failure listing every problem; never fail on the first and hide the rest.
- Messages name the **environment variable** (`DATABASE_PASSWORD`), not only the property key.
- Absent, blank, or an unresolved `${...}` literal all count as missing.
- The existing 25 backend tests stay green with no new infrastructure.
- Secrets stay out of source control; test fixtures are obviously non-production.

**Ask First:**
- Making any property mandatory beyond the three named here.
- Turning the JWT length floor into an algorithm-specific key-strength rule — that is a Phase 1 decision made with the signing algorithm.
- Any approach needing a real-looking secret committed to satisfy tests.

**Never:**
- JWT token generation, parsing, or signing; auth filter; `UserDetailsService`; RBAC rules; business modules.
- Reintroducing a hardcoded credential fallback.
- Editing approved specifications under `Documents/` or `_bmad-output/specs/`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| All present | password set; both secrets ≥ 32 chars | Startup proceeds | N/A |
| Password missing | `DATABASE_PASSWORD` unset | Aborts naming `DATABASE_PASSWORD` | Raised before `DataSource` creation |
| Both secrets missing | `JWT_SECRET`, `JWT_REFRESH_SECRET` unset | Aborts listing **both** at once | One aggregated exception |
| Secret too short | `JWT_SECRET=abc` | Aborts naming `JWT_SECRET` and the floor | Aggregated |
| Blank value | `DATABASE_PASSWORD="   "` | Treated as missing | Aggregated |
| Unresolved placeholder | resolves to literal `${DATABASE_PASSWORD}` | Treated as missing | Aggregated |
| Mixed failures | password missing **and** secret short | Both in one message | Aggregated |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/pos/common/config/` — target package; holds `CorrelationIdFilter`, `RequestCorrelation`, `OpenApiConfig`.
- `backend/src/main/resources/application.yml` — read-only here. Declares the three properties under validation; do not reintroduce defaults.
- `backend/src/main/resources/META-INF/spring.factories` — does not exist; must be created. `EnvironmentPostProcessor` is still registered via `spring.factories` in Boot 3.2 (only auto-configurations moved to `AutoConfiguration.imports`).
- `backend/src/test/resources/application-test.yml` — sets `spring.datasource.password` but no `jwt.*`; making secrets mandatory breaks all 25 tests unless fixtures are added.
- `backend/src/test/java/com/pos/AbstractIntegrationTest.java` — base class for the context-backed tests; the regression surface.
- `backend/src/test/java/com/pos/common/exception/GlobalExceptionHandlerTests.java` — reuse pointer: existing plain-JUnit, no-Spring-context test pattern.

**Ordering constraint:** `ConfigDataEnvironmentPostProcessor` runs at `HIGHEST_PRECEDENCE + 10`. The validator must order itself *after* it, or `application-test.yml` will not yet be in the `Environment` and every test fails validation.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java` — implement `EnvironmentPostProcessor, Ordered`; declare required properties with env-var name and minimum length; aggregate problems; throw with a remedy — single place that owns the contract.
- [x] `backend/src/main/resources/META-INF/spring.factories` — register under `org.springframework.boot.env.EnvironmentPostProcessor` — required for Boot to invoke it.
- [x] `backend/src/test/resources/application-test.yml` — add clearly-labelled non-production `jwt.*` fixtures — keeps the suite green.
- [x] `backend/src/test/java/com/pos/common/config/RequiredConfigurationValidatorTests.java` — plain JUnit over `MockEnvironment` covering every matrix row — behaviour, not wiring.
- [x] `README.md` — amend only if the new behaviour makes existing wording inaccurate. `.env.example` already documents all three variables.

**Acceptance Criteria:**
- Given all required configuration is present, when the context starts, then startup completes and no existing test changes behaviour.
- Given a required variable is unset, when startup runs, then the message names that environment variable and the process exits before any database connection is attempted.
- Given the length floor is enforced, when it is read in code, then a comment states it is provisional pending the Phase 1 signing-algorithm decision.

## Spec Change Log

## Design Notes

The JWT floor is **32 characters**, matching the `openssl rand -hex 32` guidance already in `.env.example`. It is configuration hygiene, not cryptographic policy — it stops `JWT_SECRET=x` reaching Phase 1. No approved document specifies a key length, because no signing algorithm has been chosen.

Aggregation matters more than it looks: a first-time setup has all three variables unset, and failing on the first costs three start-fix-restart cycles.

## Verification

**Commands:**
- `cd backend && mvn -B clean verify` — expected: `BUILD SUCCESS`, 25 existing + new validator tests, 0 failures.
- `cd backend && java -jar target/pos-backend-0.0.1-SNAPSHOT.jar` with no env set — expected: abort naming all three variables; no Postgres authentication error.
- Same command with all three supplied — expected: Flyway migrates, Tomcat starts on 8080.
- `git status --porcelain` — expected: no `.env`, no secret material, no build output.

## Suggested Review Order

**The validation contract**

- Start here: the whole contract in one list — what is mandatory, and why.
  [`RequiredConfigurationValidator.java:37`](../../backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java#L37)

- Aggregates every problem before throwing, so one restart reveals all of them.
  [`RequiredConfigurationValidator.java:56`](../../backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java#L56)

- The riskiest line in the change: ordering after ConfigData, or every test fails.
  [`RequiredConfigurationValidator.java:76`](../../backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java#L76)

- Absent, blank, and unresolved-placeholder collapse into one "missing" rule.
  [`RequiredConfigurationValidator.java:123`](../../backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java#L123)

- Swallows the resolver throw; an unresolvable placeholder is itself the "not set" case.
  [`RequiredConfigurationValidator.java:111`](../../backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java#L111)

**Judgement calls worth challenging**

- The 32-character floor: hygiene, not crypto policy. Provisional pending Phase 1.
  [`RequiredConfigurationValidator.java:35`](../../backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java#L35)

- Failure text tells the operator how to fix it without echoing any value.
  [`RequiredConfigurationValidator.java:135`](../../backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java#L135)

**Wiring**

- Boot only invokes the validator because of this registration.
  [`spring.factories:3`](../../backend/src/main/resources/META-INF/spring.factories#L3)

**Tests and supporting changes**

- The defect that motivated the story: literal placeholder passthrough.
  [`RequiredConfigurationValidatorTests.java:63`](../../backend/src/test/java/com/pos/common/config/RequiredConfigurationValidatorTests.java#L63)

- Guards the ordering constraint that a unit test can otherwise miss.
  [`RequiredConfigurationValidatorTests.java:100`](../../backend/src/test/java/com/pos/common/config/RequiredConfigurationValidatorTests.java#L100)

- Asserts a rejected secret never reaches the logs.
  [`RequiredConfigurationValidatorTests.java:92`](../../backend/src/test/java/com/pos/common/config/RequiredConfigurationValidatorTests.java#L92)

- Fixtures the suite now needs because the secrets became mandatory.
  [`application-test.yml:20`](../../backend/src/test/resources/application-test.yml#L20)

- Corrects wording that the new behaviour made false.
  [`.env.example:26`](../../.env.example#L26)

- Documents all four required variables and the aggregated failure.
  [`README.md:54`](../../README.md#L54)
