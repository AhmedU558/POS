package com.pos.users.domain;

import java.util.Set;

/**
 * Stable permission codes.
 *
 * <p>REST API Specification section 4.3 requires authorization to key on codes rather than role
 * names. They are declared here so that a check reads {@code PermissionCode.USER_ADMIN} and fails
 * to compile on a typo, instead of hand-typing {@code "USER_ADMN"} and failing closed at runtime.
 *
 * <p>Only identity codes live here. Each module contributes its own codes with the migration that
 * introduces it, so no code exists for an endpoint that has not been built.
 */
public final class PermissionCode {

    public static final String USER_READ = "USER_READ";
    public static final String USER_WRITE = "USER_WRITE";
    public static final String USER_ADMIN = "USER_ADMIN";
    public static final String ROLE_READ = "ROLE_READ";
    public static final String ROLE_WRITE = "ROLE_WRITE";

    /** Every identity code, matching what {@code V2__seed_identity_reference_data.sql} seeds. */
    public static final Set<String> IDENTITY =
            Set.of(USER_READ, USER_WRITE, USER_ADMIN, ROLE_READ, ROLE_WRITE);

    private PermissionCode() {}
}
