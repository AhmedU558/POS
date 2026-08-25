package com.pos.common.response;

import org.springframework.http.HttpStatus;

/**
 * Canonical API error codes.
 *
 * <p>Defined by the REST API Specification section 28 ("Error Codes"). HTTP status mappings are
 * constrained to the status set declared in section 3 ("HTTP Conventions").
 */
public enum ErrorCode {

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "One or more fields are invalid."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Requested record does not exist."),
    RESOURCE_INACTIVE(HttpStatus.CONFLICT, "Requested business resource is inactive."),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Request violates a business rule."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Required stock is unavailable."),
    REGISTER_SESSION_REQUIRED(HttpStatus.CONFLICT, "Operation requires an open register session."),
    REGISTER_ALREADY_OPEN(HttpStatus.CONFLICT, "Register already has an active session."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "Idempotency or external identifier conflict."),
    PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Payment operation failed."),
    REFUND_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Refund cannot be performed."),
    CONFLICT(HttpStatus.CONFLICT, "Resource state conflicts with the requested operation."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
