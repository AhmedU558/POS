package com.pos.audit.domain;

/**
 * Optional HTTP context for an audited action, per Database Design section 20.1
 * ({@code ip_address}, {@code user_agent}), both of which that section marks "when available".
 *
 * <p>Absent for system-initiated actions, which arrive through no request at all.
 */
public record AuditRequestContext(String ipAddress, String userAgent) {

    private static final AuditRequestContext NONE = new AuditRequestContext(null, null);

    /**
     * Both values originate in client-controlled headers. {@code ip_address} is a native
     * {@code inet} column and {@code user_agent} is unbounded {@code TEXT}, so unchecked input
     * would either abort the audited business operation with a cast error or store megabytes in a
     * table nothing is permitted to delete.
     */
    private static final int USER_AGENT_MAX_LENGTH = 512;

    private static final java.util.regex.Pattern INET_LITERAL =
            java.util.regex.Pattern.compile("[0-9A-Fa-f.:%]{2,45}");

    /** No request context — the correct value for system-initiated actions. */
    public static AuditRequestContext none() {
        return NONE;
    }

    public static AuditRequestContext of(String ipAddress, String userAgent) {
        return new AuditRequestContext(sanitiseIp(ipAddress), truncate(userAgent));
    }

    /**
     * Keeps only a genuine IP literal. An {@code X-Forwarded-For} chain, the literal string
     * "unknown", or anything else a proxy invents is discarded rather than thrown, because a
     * malformed header must never abort the operation being audited.
     */
    private static String sanitiseIp(String candidate) {
        if (candidate == null || candidate.isBlank() || !INET_LITERAL.matcher(candidate).matches()) {
            return null;
        }
        try {
            // Safe on literals only, which the pattern above has already guaranteed: this cannot
            // trigger a DNS lookup.
            java.net.InetAddress.getByName(candidate);
            return candidate;
        } catch (java.net.UnknownHostException ex) {
            return null;
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= USER_AGENT_MAX_LENGTH
                ? userAgent
                : userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
