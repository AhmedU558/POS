package com.pos.common.config;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Access to the correlation identifier assigned to the current request.
 *
 * <p>Required by System Architecture Document section 21 ("Every request should have a
 * correlation/request ID") and REST API Specification section 29.
 */
public final class RequestCorrelation {

    /** Inbound/outbound HTTP header carrying the correlation identifier. */
    public static final String HEADER = "X-Request-Id";

    /** SLF4J MDC key, referenced by the logging pattern in application.yml. */
    public static final String MDC_KEY = "requestId";

    private RequestCorrelation() {}

    /**
     * Returns the current request's correlation identifier, generating a fallback when called
     * outside a request scope so that response envelopes always carry a value.
     */
    public static String currentId() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }

    static void set(String id) {
        MDC.put(MDC_KEY, id);
    }

    static void clear() {
        MDC.remove(MDC_KEY);
    }
}
