package com.pos.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiErrorResponse;
import com.pos.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders the standard error envelope when an authenticated principal lacks the required
 * permission, per REST API Specification sections 5.2 and 28 ({@code ACCESS_DENIED}).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {

        ApiErrorResponse body =
                ApiErrorResponse.of(
                        ErrorCode.ACCESS_DENIED,
                        ErrorCode.ACCESS_DENIED.defaultMessage(),
                        RequestCorrelation.currentId());

        response.setStatus(ErrorCode.ACCESS_DENIED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
