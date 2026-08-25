package com.pos.common.exception;

import com.pos.common.response.ApiErrorDetail;
import com.pos.common.response.ErrorCode;

import java.util.List;

/**
 * Base exception for business and application errors that map onto a documented
 * {@link ErrorCode} from REST API Specification section 28.
 *
 * <p>Modules throw this (or a subclass) instead of returning ad-hoc error responses, so that every
 * failure is rendered through {@link GlobalExceptionHandler} in the standard envelope.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<ApiErrorDetail> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ApiException(ErrorCode errorCode, String message, List<ApiErrorDetail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<ApiErrorDetail> details() {
        return details;
    }
}
