<!-- Story 0.1 review layer. Subagents were declined for this workflow run, so the
     BMad build workflow requires each reviewer prompt to be written out for a human
     to run in a separate session (ideally a different LLM). Paste everything below
     the divider as the prompt, then paste the findings back into the build session. -->

# Reviewer: blind-hunter

---

Conduct a review of CONTENT.
Look for what's missing, not only what's wrong.
Find at least ten issues to fix or improve.
Output a Markdown list of findings only — no severity, priority, or ranking.
If the content is empty, stop and say so.
If you have zero findings, re-check and keep thinking; do not stop with an empty list.

CONTENT:
### Tracked changes since 417165d22cfdad4ad5f0ccfe2be1727fc9352734

diff --git a/.env.example b/.env.example
index 3c286f2..ce0a3fe 100644
--- a/.env.example
+++ b/.env.example
@@ -24,7 +24,10 @@ CORS_ALLOWED_ORIGINS=http://localhost:3000
 SPRINGDOC_ENABLED=true
 
 # ---- Authentication ---------------------------------------------------------
-# Required from Phase 1 onward. Generate per environment, for example:
+# Required. The application refuses to start without both, and each must be at
+# least 32 characters. Nothing signs a token yet — the check exists so a weak or
+# missing secret cannot reach the authentication work in Phase 1.
+# Generate each one separately, for example:
 #   openssl rand -hex 32
 # Never reuse a value across environments and never commit a real value.
 JWT_SECRET=
diff --git a/README.md b/README.md
index d578f35..1bb7732 100644
--- a/README.md
+++ b/README.md
@@ -49,16 +49,28 @@ _bmad-output/ BMad artefacts (SPEC kernel and companions)
 cp .env.example .env
 ```
 
-Fill in `.env`. `POSTGRES_PASSWORD` and `DATABASE_PASSWORD` are required — the API refuses to
-start without a database password, because credentials must never be committed to source control
-(SAD section 15, Database Design section 28).
+Fill in `.env`. Credentials are never committed to source control (SAD section 15, Database
+Design section 28), so the application validates them at startup and refuses to run if any are
+missing. Four values are required:
 
-Generate the Phase 1 authentication secrets with a real generator, never by hand:
+| Variable | Notes |
+|---|---|
+| `POSTGRES_PASSWORD` | Used by docker-compose to provision the local database |
+| `DATABASE_PASSWORD` | Used by the API to connect to it |
+| `JWT_SECRET` | At least 32 characters |
+| `JWT_REFRESH_SECRET` | At least 32 characters, different from `JWT_SECRET` |
+
+Nothing signs a token until Phase 1, but the secrets are validated now so a weak or missing value
+cannot reach the authentication work. Generate each one separately with a real generator, never
+by hand:
 
 ```bash
 openssl rand -hex 32
 ```
 
+If any are missing, startup aborts with a message naming every offending variable at once, so a
+first-time setup takes one fix rather than four.
+
 ## Start the local environment
 
 **1. Backing services**
diff --git a/backend/src/test/resources/application-test.yml b/backend/src/test/resources/application-test.yml
index 5ca1567..b74da43 100644
--- a/backend/src/test/resources/application-test.yml
+++ b/backend/src/test/resources/application-test.yml
@@ -16,3 +16,10 @@ app:
   security:
     cors:
       allowed-origins: http://localhost:3000
+
+jwt:
+  # Non-production fixtures. Required because RequiredConfigurationValidator makes the signing
+  # secrets mandatory at startup. These are deliberately obvious placeholders, long enough only
+  # to clear the configuration-hygiene floor; nothing signs or verifies a token in Phase 0.
+  secret: test-fixture-not-a-real-secret-value-0001
+  refresh-secret: test-fixture-not-a-real-secret-value-0002

### New (untracked) files since baseline

--- new file: _bmad-output/implementation-artifacts/spec-0-1-startup-config-validation.md ---
---
title: 'Story 0.1 — Startup validation of required configuration'
type: 'feature'
created: '2026-08-25'
status: 'in-review'
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

--- new file: backend/src/main/java/com/pos/common/config/RequiredConfigurationValidator.java ---
package com.pos.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fails startup when configuration the application cannot run without is missing.
 *
 * <p>Credentials are supplied only by the environment (SAD section 15, SRS section 9, Database
 * Design section 28), so {@code application.yml} declares them as bare {@code ${ENV_VAR}}
 * placeholders with no defaults. Spring Boot's binder does not treat an unresolvable placeholder as
 * an error — it passes the literal text through — so without this check a missing
 * {@code DATABASE_PASSWORD} reaches PostgreSQL and comes back as an authentication failure that
 * points at the database rather than at the unset variable.
 *
 * <p>Runs as an {@link EnvironmentPostProcessor} so the check happens while the environment is
 * being prepared, before any {@code DataSource} is built.
 */
public class RequiredConfigurationValidator implements EnvironmentPostProcessor, Ordered {

    /**
     * Minimum length for signing secrets, matching the {@code openssl rand -hex 32} guidance in
     * {@code .env.example}.
     *
     * <p>This is configuration hygiene, not cryptographic policy: it exists to stop a value like
     * {@code JWT_SECRET=x} reaching Phase 1. No approved document specifies a key length because
     * no signing algorithm has been selected yet. Revisit this floor when that decision is made.
     */
    private static final int MINIMUM_SECRET_LENGTH = 32;

    private static final List<RequiredProperty> REQUIRED =
            List.of(
                    new RequiredProperty(
                            "spring.datasource.password",
                            "DATABASE_PASSWORD",
                            0,
                            "the password for the application database user"),
                    new RequiredProperty(
                            "jwt.secret",
                            "JWT_SECRET",
                            MINIMUM_SECRET_LENGTH,
                            "the access-token signing secret"),
                    new RequiredProperty(
                            "jwt.refresh-secret",
                            "JWT_REFRESH_SECRET",
                            MINIMUM_SECRET_LENGTH,
                            "the refresh-token signing secret"));

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {

        List<String> problems = new ArrayList<>();
        for (RequiredProperty required : REQUIRED) {
            required.validate(environment).ifPresent(problems::add);
        }

        if (!problems.isEmpty()) {
            throw new MissingConfigurationException(problems);
        }
    }

    /**
     * Ordered after {@code ConfigDataEnvironmentPostProcessor} (which runs at
     * {@code HIGHEST_PRECEDENCE + 10}) so that profile-specific files such as
     * {@code application-test.yml} have already contributed their property sources. Validating
     * before them would reject every profile that supplies its own values.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * One mandatory setting: where it lives, where its value comes from, and how long it must be.
     *
     * @param propertyKey Spring property key to resolve
     * @param environmentVariable variable the operator actually sets; named in failure messages
     * @param minimumLength minimum accepted length, or {@code 0} for "any non-blank value"
     * @param purpose short human description used in the failure message
     */
    private record RequiredProperty(
            String propertyKey, String environmentVariable, int minimumLength, String purpose) {

        Optional<String> validate(ConfigurableEnvironment environment) {
            String value = resolve(environment);

            if (isMissing(value)) {
                return Optional.of(
                        "%s is not set. It supplies %s (property %s)."
                                .formatted(environmentVariable, purpose, propertyKey));
            }
            if (value.length() < minimumLength) {
                return Optional.of(
                        "%s is too short: %d characters, minimum %d."
                                .formatted(environmentVariable, value.length(), minimumLength));
            }
            return Optional.empty();
        }

        /**
         * Reads the property, tolerating an unresolvable placeholder. {@code getProperty} throws
         * when a nested placeholder cannot be resolved, which is itself the "not set" case.
         */
        private String resolve(ConfigurableEnvironment environment) {
            try {
                return environment.getProperty(propertyKey);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        /**
         * Absent, blank, and "still a literal {@code ${...}} placeholder" are the same failure to
         * an operator: no usable value was supplied.
         */
        private boolean isMissing(String value) {
            if (value == null || value.isBlank()) {
                return true;
            }
            String trimmed = value.trim();
            return trimmed.startsWith("${") && trimmed.endsWith("}");
        }
    }

    /** Aggregates every configuration problem so one restart reveals all of them. */
    static class MissingConfigurationException extends IllegalStateException {

        private static final String REMEDY =
                """

                Copy .env.example to .env, fill in the values, and export them before starting.
                Generate each secret separately, for example: openssl rand -hex 32
                Never commit real values.""";

        MissingConfigurationException(List<String> problems) {
            super(buildMessage(problems));
        }

        private static String buildMessage(List<String> problems) {
            StringBuilder message =
                    new StringBuilder("Application configuration is incomplete:\n");
            problems.forEach(problem -> message.append("  - ").append(problem).append('\n'));
            return message.append(REMEDY).toString();
        }
    }
}

--- new file: backend/src/main/resources/META-INF/spring.factories ---
# EnvironmentPostProcessor implementations are still registered through spring.factories in
# Spring Boot 3.x; only auto-configurations moved to AutoConfiguration.imports.
org.springframework.boot.env.EnvironmentPostProcessor=\
com.pos.common.config.RequiredConfigurationValidator

--- new file: backend/src/test/java/com/pos/common/config/RequiredConfigurationValidatorTests.java ---
package com.pos.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers every row of the I/O and edge-case matrix in the Story 0.1 spec.
 *
 * <p>Runs without a Spring context: the validator is an {@code EnvironmentPostProcessor}, so
 * driving it directly against a {@link MockEnvironment} tests the decision logic rather than the
 * framework wiring that invokes it.
 */
class RequiredConfigurationValidatorTests {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    private final RequiredConfigurationValidator validator = new RequiredConfigurationValidator();

    @Test
    void completeConfigurationStartsCleanly() {
        assertThatCode(() -> validate(fullyConfigured())).doesNotThrowAnyException();
    }

    @Test
    void missingDatabasePasswordNamesTheEnvironmentVariable() {
        assertThatThrownBy(() -> validate(configured(null, VALID_SECRET, VALID_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_PASSWORD is not set")
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void bothMissingSecretsAreReportedInASingleFailure() {
        assertThatThrownBy(() -> validate(configured("dbpass", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set")
                .hasMessageContaining("JWT_REFRESH_SECRET is not set");
    }

    @Test
    void secretShorterThanTheFloorIsRejectedWithTheLengths() {
        assertThatThrownBy(() -> validate(configured("dbpass", "abc", VALID_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is too short")
                .hasMessageContaining("3 characters")
                .hasMessageContaining("minimum 32");
    }

    @Test
    void blankValueCountsAsMissing() {
        assertThatThrownBy(() -> validate(configured("   ", VALID_SECRET, VALID_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_PASSWORD is not set");
    }

    @Test
    void unresolvedPlaceholderLiteralCountsAsMissing() {
        // The failure this whole class exists for: Spring Boot's binder passes an unresolvable
        // placeholder through as literal text rather than failing.
        assertThatThrownBy(
                        () ->
                                validate(
                                        configured(
                                                "${DATABASE_PASSWORD}", VALID_SECRET, VALID_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_PASSWORD is not set");
    }

    @Test
    void mixedFailuresAreAggregatedIntoOneMessage() {
        assertThatThrownBy(() -> validate(configured(null, "short", VALID_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_PASSWORD is not set")
                .hasMessageContaining("JWT_SECRET is too short");
    }

    @Test
    void failureMessageTellsTheOperatorHowToFixIt() {
        assertThatThrownBy(() -> validate(configured(null, null, null)))
                .hasMessageContaining(".env.example")
                .hasMessageContaining("openssl rand -hex 32")
                .hasMessageContaining("Never commit real values");
    }

    @Test
    void failureMessageNeverEchoesASuppliedValue() {
        // A configuration error must not print the secret it rejected into the logs.
        assertThatThrownBy(() -> validate(configured("dbpass", "sensitive-but-far-too-short", null)))
                .hasMessageNotContaining("sensitive-but-far-too-short")
                .hasMessageNotContaining("dbpass");
    }

    @Test
    void runsAfterConfigDataSoProfileSpecificFilesAreVisible() {
        // ConfigDataEnvironmentPostProcessor sits at HIGHEST_PRECEDENCE + 10. Ordering before it
        // would mean application-test.yml has not been contributed yet and every profile that
        // supplies its own values would be rejected.
        assertThat(validator.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE + 10);
    }

    private void validate(MockEnvironment environment) {
        validator.postProcessEnvironment(environment, new SpringApplication());
    }

    private MockEnvironment fullyConfigured() {
        return configured("dbpass", VALID_SECRET, VALID_SECRET);
    }

    private MockEnvironment configured(String password, String secret, String refreshSecret) {
        MockEnvironment environment = new MockEnvironment();
        setIfPresent(environment, "spring.datasource.password", password);
        setIfPresent(environment, "jwt.secret", secret);
        setIfPresent(environment, "jwt.refresh-secret", refreshSecret);
        return environment;
    }

    private void setIfPresent(MockEnvironment environment, String key, String value) {
        if (value != null) {
            environment.setProperty(key, value);
        }
    }
}



Do not invoke any skill. Return only the review result.
