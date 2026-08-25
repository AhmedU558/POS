package com.pos.bootstrap.service;

import com.pos.bootstrap.config.BootstrapProperties;
import org.springframework.stereotype.Component;

import com.pos.bootstrap.config.BootstrapProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Produces the initial administrator password from the operator's chosen source.
 *
 * <p>A mounted secret file is tried first and the environment value second. There is deliberately
 * no third source and no default: a hardcoded fallback would be a committed credential, and a
 * silent fallback would mean a deployment that believed it was reading a secret file was quietly
 * using something else.
 */
@Component
public class BootstrapCredentialResolver {

    /** A password file holding more than this is not a password. */
    private static final long MAX_SECRET_FILE_BYTES = 4096;

    private final BootstrapProperties properties;

    public BootstrapCredentialResolver(BootstrapProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws IllegalStateException when no usable credential exists, so bootstrap fails closed
     *     rather than inventing one
     */
    public String resolve() {
        String fromFile = readSecretFile();
        if (fromFile != null) {
            return fromFile;
        }
        String fromProperty = properties.getPassword();
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        throw new IllegalStateException(
                "Bootstrap is enabled but no administrator password was supplied."
                        + " Set app.bootstrap.password-file (preferred) or app.bootstrap.password.");
    }

    /**
     * Reads the secret file when one is configured.
     *
     * <p>An unreadable or empty file is an error, never a reason to fall through to the
     * environment: an operator who configured a file expects the file to be used.
     */
    private String readSecretFile() {
        String location = properties.getPasswordFile();
        if (location == null || location.isBlank()) {
            return null;
        }
        Path path;
        try {
            path = Path.of(location);
        } catch (InvalidPathException ex) {
            throw new IllegalStateException("Bootstrap password file path is invalid: " + location, ex);
        }
        String contents;
        try {
            // Guard the shape before reading: a device node or a huge file would otherwise be
            // slurped into memory at startup.
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException(
                        "Bootstrap password file is not a regular file: " + location);
            }
            if (Files.size(path) > MAX_SECRET_FILE_BYTES) {
                throw new IllegalStateException(
                        "Bootstrap password file is implausibly large: " + location);
            }
            contents = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Bootstrap password file could not be read: " + location, ex);
        }
        // Trailing newlines are near-universal in mounted secrets and in `echo` mistakes alike.
        String trimmed = contents.strip();
        if (trimmed.isEmpty()) {
            // A zero-byte secret mount is a common Kubernetes misconfiguration. Returning "" here
            // would provision the super-administrator with an empty password.
            throw new IllegalStateException("Bootstrap password file is empty: " + location);
        }
        if (trimmed.lines().count() > 1) {
            throw new IllegalStateException(
                    "Bootstrap password file must contain exactly one line: " + location);
        }
        if (trimmed.length() < BootstrapProperties.MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "Bootstrap password file supplies fewer than "
                            + BootstrapProperties.MINIMUM_PASSWORD_LENGTH
                            + " characters: "
                            + location);
        }
        return trimmed;
    }
}
