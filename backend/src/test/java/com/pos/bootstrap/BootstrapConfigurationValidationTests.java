package com.pos.bootstrap;

import com.pos.bootstrap.config.BootstrapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a misconfigured bootstrap stops the application from starting at all.
 *
 * <p>Deliberately not a {@code @SpringBootTest}: the guarantee is that the <em>context refresh</em>
 * fails, which a started context cannot demonstrate. {@link ApplicationContextRunner} builds a
 * context from real property values and lets the assertion inspect the failure, so both the
 * binding and the {@code @PostConstruct} hook are under test — the other bootstrap tests drive
 * {@link BootstrapProperties} through setters and would stay green if either were removed.
 *
 * <p>No database is involved, so these run in milliseconds.
 */
class BootstrapConfigurationValidationTests {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                    .withUserConfiguration(BootstrapProperties.class);

    @Test
    void aDisabledBootstrapNeedsNoConfigurationAtAll() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void aCompletelyConfiguredBootstrapStartsCleanly() {
        contextRunner
                .withPropertyValues(
                        "app.bootstrap.enabled=true",
                        "app.bootstrap.username=ada.lovelace",
                        "app.bootstrap.first-name=Ada",
                        "app.bootstrap.last-name=Lovelace",
                        "app.bootstrap.password=a-sufficiently-long-password")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void anEnabledButUnnamedAdministratorStopsTheContextFromRefreshing() {
        // Without the @PostConstruct hook this would start normally and fail only later, which is
        // exactly the "half-configured bootstrap quietly does nothing" outcome fail-closed exists
        // to prevent.
        contextRunner
                .withPropertyValues(
                        "app.bootstrap.enabled=true",
                        "app.bootstrap.password=a-sufficiently-long-password")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("app.bootstrap.username")
                                        .hasMessageContaining("app.bootstrap.first-name")
                                        .hasMessageContaining("app.bootstrap.last-name"));
    }

    @Test
    void anEnabledBootstrapWithNoCredentialStopsTheContextFromRefreshing() {
        contextRunner
                .withPropertyValues(
                        "app.bootstrap.enabled=true",
                        "app.bootstrap.username=ada.lovelace",
                        "app.bootstrap.first-name=Ada",
                        "app.bootstrap.last-name=Lovelace")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("administrator password"));
    }

    @Test
    void aWeakPasswordStopsTheContextFromRefreshing() {
        contextRunner
                .withPropertyValues(
                        "app.bootstrap.enabled=true",
                        "app.bootstrap.username=ada.lovelace",
                        "app.bootstrap.first-name=Ada",
                        "app.bootstrap.last-name=Lovelace",
                        "app.bootstrap.password=short")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("at least 12 characters"));
    }

    @Test
    void propertyKeysBindInTheirKebabCaseAndEnvironmentForms() {
        // Operators supply these as APP_BOOTSTRAP_FIRST_NAME in a container environment; relaxed
        // binding is what makes that reach first-name, and nothing else asserts it.
        contextRunner
                .withPropertyValues(
                        "app.bootstrap.enabled=true",
                        "app.bootstrap.username=ada.lovelace",
                        "app.bootstrap.first-name=Ada",
                        "app.bootstrap.last-name=Lovelace",
                        "app.bootstrap.password-file=/run/secrets/admin-password")
                .run(
                        context -> {
                            BootstrapProperties properties = context.getBean(BootstrapProperties.class);
                            assertThat(properties.getFirstName()).isEqualTo("Ada");
                            assertThat(properties.getPasswordFile())
                                    .isEqualTo("/run/secrets/admin-password");
                        });
    }
}
