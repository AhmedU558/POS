package com.pos.common.response;

/**
 * Standard success response envelope defined by REST API Specification section 5.1.
 *
 * @param <T> payload type
 */
public record ApiResponse<T>(T data, ApiMeta meta) {

    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, ApiMeta.of(requestId));
    }

    public static <T> ApiResponse<T> of(T data, ApiMeta meta) {
        return new ApiResponse<>(data, meta);
    }
}
