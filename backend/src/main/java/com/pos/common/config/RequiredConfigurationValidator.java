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
