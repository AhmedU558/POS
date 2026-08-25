package com.pos.bootstrap.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for first-administrator provisioning.
 *
 * <p>Every field is operator-supplied and none has a default. The account this produces holds every
 * identity permission in the system, so it must be an identifiable person chosen deliberately, not
 * a generic {@code admin} the configuration invented (ADR-015).
 *
 * <p>Validation runs at context refresh, which means a misconfigured deployment fails before the
 * application serves a single request rather than at the moment bootstrap would have run.
 */
@Component
@ConfigurationProperties(prefix = "app.bootstrap")
public class BootstrapProperties {

    /**
     * Bootstrap runs only when this is explicitly true.
     *
     * <p>It never triggers merely because no administrator exists: inferring intent from absent
     * state is what would let a deleted administrator be silently rebuilt.
     */
    private boolean enabled = false;

    /** Approved password policy (AMD-002): length only, no composition rules. */
    public static final int MINIMUM_PASSWORD_LENGTH = 12;

    private static final int USERNAME_MAX_LENGTH = 100;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int EMAIL_MAX_LENGTH = 255;

    private String username;
    private String firstName;
    private String lastName;
    private String email;

    /** Plain value. Acceptable for local development; production should prefer the file. */
    private String password;

    /**
     * Path to a mounted secret file, tried before {@link #password}.
     *
     * <p>The {@code _FILE} convention matches the official Postgres and MySQL images, so Docker and
     * Kubernetes secret mounts work without translation and the value never enters the process
     * environment where {@code docker inspect} or a crash dump would expose it.
     */
    private String passwordFile;

    /**
     * Fails startup when bootstrap is switched on but cannot be carried out.
     *
     * <p>Fail closed: a half-configured bootstrap must never quietly do nothing, because the
     * operator would conclude provisioning had run.
     */
    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        List<String> problems = new ArrayList<>();
        // Bounded to the column widths in Database Design §6.1. Without this an over-long name
        // fails as a value-too-long violation during provisioning, which the runner cannot tell
        // apart from a genuine failure and which would crash-loop every replica.
        requireText(username, "app.bootstrap.username", USERNAME_MAX_LENGTH, problems);
        requireText(firstName, "app.bootstrap.first-name", NAME_MAX_LENGTH, problems);
        requireText(lastName, "app.bootstrap.last-name", NAME_MAX_LENGTH, problems);
        if (hasText(email) && email.length() > EMAIL_MAX_LENGTH) {
            problems.add("app.bootstrap.email must be at most " + EMAIL_MAX_LENGTH + " characters");
        }
        if (!hasText(password) && !hasText(passwordFile)) {
            problems.add(
                    "app.bootstrap.password-file or app.bootstrap.password must supply the"
                            + " initial administrator password");
        }
        // The approved policy (AMD-002): minimum 12 characters, no composition rules. Applied
        // here too -- this credential opens the most privileged account in the deployment.
        if (hasText(password) && password.length() < MINIMUM_PASSWORD_LENGTH) {
            problems.add(
                    "app.bootstrap.password must be at least "
                            + MINIMUM_PASSWORD_LENGTH
                            + " characters");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Bootstrap is enabled but incomplete:\n  - "
                            + String.join("\n  - ", problems)
                            + "\n\nSupply the missing values or set app.bootstrap.enabled=false."
                            + " Never commit a real credential.");
        }
    }

    private static void requireText(
            String value, String key, int maxLength, List<String> problems) {
        if (!hasText(value)) {
            problems.add(key + " must be set; the administrator must be a named person");
        } else if (value.length() > maxLength) {
            problems.add(key + " must be at most " + maxLength + " characters");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordFile() {
        return passwordFile;
    }

    public void setPasswordFile(String passwordFile) {
        this.passwordFile = passwordFile;
    }
}
