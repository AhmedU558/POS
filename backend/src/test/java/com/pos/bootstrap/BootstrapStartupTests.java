package com.pos.bootstrap;

import com.pos.TestcontainersConfiguration;
import com.pos.bootstrap.config.BootstrapProperties;
import com.pos.bootstrap.repository.BootstrapCompletionRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that provisioning actually happens <em>at startup</em>, driven by real configuration.
 *
 * <p>Every other bootstrap test calls {@code runBootstrap()} itself, which verifies the logic but
 * not the trigger: removing {@code implements ApplicationRunner} would leave all of them green
 * while no deployment ever provisioned an administrator. This context is started with
 * {@code app.bootstrap.*} set and asserts the result without invoking anything, so both the runner
 * callback and the {@code @ConfigurationProperties} binding are under test.
 *
 * <p>Its own context, dirtied afterwards, so an administrator existing does not leak into the
 * shared context every other integration test uses.
 */
@SpringBootTest(
        properties = {
            "app.bootstrap.enabled=true",
            "app.bootstrap.username=startup.administrator",
            "app.bootstrap.first-name=Startup",
            "app.bootstrap.last-name=Administrator",
            "app.bootstrap.email=startup@example.test",
            "app.bootstrap.password=provisioned-at-startup-correctly"
        })
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BootstrapStartupTests {

    private static final String USERNAME = "startup.administrator";

    @Autowired private UserRepository userRepository;
    @Autowired private BootstrapCompletionRepository completionRepository;
    @Autowired private BootstrapProperties properties;

    @Test
    void startingTheApplicationProvisionsTheAdministrator() {
        // Nothing is called here. If the ApplicationRunner callback stops firing, this fails.
        User administrator = userRepository.findByUsername(USERNAME).orElseThrow();

        assertThat(administrator.getFirstName()).isEqualTo("Startup");
        assertThat(administrator.isPasswordChangeRequired()).isTrue();
        assertThat(completionRepository.count()).isEqualTo(1);
    }

    @Test
    void operatorConfigurationIsBoundFromRealPropertyKeys() {
        // Renaming the @ConfigurationProperties prefix would silently disable bootstrap in
        // production while leaving every setter-driven test passing.
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getUsername()).isEqualTo(USERNAME);
        assertThat(properties.getFirstName()).isEqualTo("Startup");
        assertThat(properties.getLastName()).isEqualTo("Administrator");
        assertThat(properties.getEmail()).isEqualTo("startup@example.test");
    }

    @Test
    void theCompletionMarkerPointsAtTheAccountItCreated() {
        // linkAdministrator relies on dirty-checking a managed entity. If the foreign key silently
        // stayed null, no other test in the suite would notice.
        User administrator = userRepository.findByUsername(USERNAME).orElseThrow();

        assertThat(completionRepository.findFirstBy().orElseThrow().getAdministratorUserId())
                .isEqualTo(administrator.getId());
    }
}
