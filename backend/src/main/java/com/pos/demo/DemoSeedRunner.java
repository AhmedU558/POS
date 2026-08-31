package com.pos.demo;

import com.pos.demo.config.DemoSeedProperties;
import com.pos.demo.service.DemoDataSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Seeds the demonstration dataset at startup, when explicitly asked to.
 *
 * <p>Ordered after {@code BootstrapRunner} so that first-administrator provisioning — the real
 * one — always wins the race for a fresh database.
 *
 * <p>The production guard lives here rather than in the properties class because a profile is a
 * runtime fact, not configuration: an operator can leave {@code app.demo.enabled=true} in a shared
 * file and still be protected the moment the application starts as production.
 */
@Component
@Order(100)
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    /**
     * Profiles this seeder refuses to run under, whatever the configuration says.
     *
     * <p>Refusal is loud rather than silent: an operator who switched seeding on deserves to be
     * told it did not happen, and why.
     */
    private static final List<String> FORBIDDEN_PROFILES = List.of("prod", "production");

    private final DemoSeedProperties properties;
    private final DemoDataSeeder seeder;
    private final Environment environment;

    public DemoSeedRunner(
            DemoSeedProperties properties, DemoDataSeeder seeder, Environment environment) {
        this.properties = properties;
        this.seeder = seeder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        String forbidden = activeForbiddenProfile();
        if (forbidden != null) {
            log.error(
                    "Demo seeding is enabled but was REFUSED: the '{}' profile is active."
                            + " Demonstration accounts must never exist in production."
                            + " Remove app.demo.* configuration from this deployment.",
                    forbidden);
            return;
        }

        DemoDataSeeder.DemoSeedSummary summary = seeder.seed();

        /*
         * WARN, not INFO: two accounts whose passwords are sitting in this process's environment
         * now exist, and INFO is routinely filtered. No credential is logged — the operator chose
         * the passwords and already knows them.
         */
        log.warn(
                "Demo data seeded. Accounts '{}' (Super Administrator) and '{}' (Cashier) can sign"
                        + " in to store '{}' on {}, with {} products ({} newly stocked). These are"
                        + " evaluation accounts: remove them before this database is used for"
                        + " anything real.",
                summary.adminUsername(),
                summary.cashierUsername(),
                summary.storeName(),
                summary.registerName(),
                summary.productCount(),
                summary.productsStocked());
    }

    /** @return the first forbidden profile that is active, or null when none is */
    private String activeForbiddenProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .filter(profile -> FORBIDDEN_PROFILES.contains(profile.toLowerCase()))
                .findFirst()
                .orElse(null);
    }
}
