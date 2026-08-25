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
