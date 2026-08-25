package com.pos.common.response;

import java.util.List;

/**
 * Standard error response envelope defined by REST API Specification section 5.2.
 */
public record ApiErrorResponse(ApiError error, ApiMeta meta) {

    public static ApiErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ApiErrorResponse(
                new ApiError(code.name(), message, null), ApiMeta.of(requestId));
    }

    public static ApiErrorResponse of(
            ErrorCode code, String message, List<ApiErrorDetail> details, String requestId) {
        return new ApiErrorResponse(
                new ApiError(code.name(), message, details), ApiMeta.of(requestId));
    }
}
