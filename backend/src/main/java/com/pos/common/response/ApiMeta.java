package com.pos.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response envelope metadata.
 *
 * <p>REST API Specification section 5.1/5.2 require {@code requestId} and {@code timestamp} on
 * every response. Section 5.3 adds the pagination members, which are omitted when absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(
        String requestId,
        Instant timestamp,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages) {

    public static ApiMeta of(String requestId) {
        return new ApiMeta(requestId, Instant.now(), null, null, null, null);
    }

    public static ApiMeta paged(
            String requestId, int page, int size, long totalElements, int totalPages) {
        return new ApiMeta(requestId, Instant.now(), page, size, totalElements, totalPages);
    }
}
