package com.pos.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiErrorResponse;
import com.pos.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders the standard error envelope when the security filter chain rejects an unauthenticated
 * request.
 *
 * <p>Without this, Spring Security's default entry point returns a body that does not match REST
 * API Specification section 5.2, because filter-chain rejections never reach
 * {@code @RestControllerAdvice}.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {

        ApiErrorResponse body =
                ApiErrorResponse.of(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        ErrorCode.AUTHENTICATION_REQUIRED.defaultMessage(),
                        RequestCorrelation.currentId());

        response.setStatus(ErrorCode.AUTHENTICATION_REQUIRED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
