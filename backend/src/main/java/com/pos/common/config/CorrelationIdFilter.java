package com.pos.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation identifier to every request, publishes it to the logging MDC, and echoes
 * it back on the response.
 *
 * <p>Registered ahead of the Spring Security filter chain so that authentication and authorization
 * failures are still correlated and still carry the identifier in their error envelope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String incoming = request.getHeader(RequestCorrelation.HEADER);
        String correlationId =
                StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();

        RequestCorrelation.set(correlationId);
        response.setHeader(RequestCorrelation.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestCorrelation.clear();
        }
    }
}
