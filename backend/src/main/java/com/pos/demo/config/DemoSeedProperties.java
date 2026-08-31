package com.pos.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the demonstration dataset.
 *
 * <p>This is an evaluation aid, not a provisioning mechanism. It exists so somebody can open a
 * fresh checkout and exercise the product → stock → sale loop without first hand-building a store,
 * a terminal, a register and a catalogue.
 *
 * <p>Two guards keep it out of a real deployment, and both must pass:
 *
 * <ol>
 *   <li>{@link #enabled} is false unless switched on explicitly. Absence of data is never taken as
 *       an invitation to seed, for the same reason first-administrator bootstrap does not infer
 *       intent from an empty {@code users} table (ADR-015).
 *   <li>{@link DemoSeedRunner} refuses to run under the {@code prod} profile whatever this says.
 * </ol>
 *
 * <p>Passwords have no defaults. A seeder that invented one would put an account with publicly
 * known credentials into every database it ever touched.
 */
@Component
@ConfigurationProperties(prefix = "app.demo")
public class DemoSeedProperties {

    /** Matches the approved password policy (AMD-002): length only, no composition rules. */
    public static final int MINIMUM_PASSWORD_LENGTH = 12;

    private boolean enabled = false;

    private String adminUsername = "demo.admin";
    private String cashierUsername = "demo.cashier";

    private String adminPassword;
    private String cashierPassword;

    /**
     * Fails startup when seeding is switched on but cannot be carried out.
     *
     * <p>Fail closed, as bootstrap does: a half-configured seed that quietly did nothing would
     * leave the operator believing accounts exist that do not.
     */
    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        List<String> problems = new ArrayList<>();
        requirePassword(adminPassword, "app.demo.admin-password", problems);
        requirePassword(cashierPassword, "app.demo.cashier-password", problems);
        requireText(adminUsername, "app.demo.admin-username", problems);
        requireText(cashierUsername, "app.demo.cashier-username", problems);
        if (adminUsername != null && adminUsername.equals(cashierUsername)) {
            problems.add("app.demo.admin-username and app.demo.cashier-username must differ");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Demo seeding is enabled but incomplete:\n  - "
                            + String.join("\n  - ", problems)
                            + "\n\nSupply the missing values or set app.demo.enabled=false."
                            + " Never commit a real credential.");
        }
    }

    private static void requirePassword(String value, String key, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(key + " must be set; the seeder will not invent a password");
        } else if (value.length() < MINIMUM_PASSWORD_LENGTH) {
            problems.add(key + " must be at least " + MINIMUM_PASSWORD_LENGTH + " characters");
        }
    }

    private static void requireText(String value, String key, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(key + " must not be blank");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getCashierUsername() {
        return cashierUsername;
    }

    public void setCashierUsername(String cashierUsername) {
        this.cashierUsername = cashierUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getCashierPassword() {
        return cashierPassword;
    }

    public void setCashierPassword(String cashierPassword) {
        this.cashierPassword = cashierPassword;
    }
}
