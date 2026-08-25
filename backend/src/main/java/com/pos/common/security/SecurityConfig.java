package com.pos.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security foundation.
 *
 * <p>Implements the filter-chain shape described in System Architecture Document section 15 and
 * the API protection rules in REST API Specification section 29. Authentication mechanics (JWT
 * validation, refresh tokens, user details) are deliberately absent; they are Phase 1 scope per
 * Implementation Plan section 11.
 *
 * <p>{@code @EnableMethodSecurity} is switched on now so that the permission codes defined in REST
 * API Specification section 4.3 have an enforcement mechanism available the moment the first
 * protected operation is written.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Correlation header, exposed to browsers so clients can report a request id on failures. */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Idempotency header required by REST API Specification section 6. */
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final List<String> allowedOrigins;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            @Value("${app.security.cors.allowed-origins}") List<String> allowedOrigins,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) {
        this.allowedOrigins = allowedOrigins;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API: no session cookie exists for an attacker to ride,
            // so CSRF protection is not applicable (SAD section 15).
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // API contract health probe (returns the standard response envelope).
                .requestMatchers("/api/v1/health").permitAll()
                // Infrastructure liveness probe used by container health checks.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Authentication endpoints are public by definition (API spec section 4.2).
                .requestMatchers("/api/v1/auth/**").permitAll()
                // OpenAPI documentation. Disabled entirely in production through springdoc
                // configuration (API spec section 32).
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                // CORS preflight must not require credentials.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            );

        // The JWT authentication filter is added here in Phase 1 (Implementation Plan section 11).

        return http.build();
    }

    /**
     * Browser access policy for the Next.js clients, which run on a different origin from the API
     * (UI/UX Specification section 4).
     *
     * <p>Origins come from configuration so that each environment declares its own, per SAD
     * section 2. Credentials are not allowed: the approved contract authenticates with an
     * {@code Authorization: Bearer} header, not cookies.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(
                List.of(
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.ACCEPT,
                        REQUEST_ID_HEADER,
                        IDEMPOTENCY_KEY_HEADER));
        configuration.setExposedHeaders(List.of(REQUEST_ID_HEADER));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /** Adaptive password encoder required by System Architecture Document section 15. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
