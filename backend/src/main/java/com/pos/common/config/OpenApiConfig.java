package com.pos.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI document configuration.
 *
 * <p>Required by System Architecture Document section 16 and REST API Specification section 32,
 * which also requires the authentication scheme to be documented. Declaring the bearer scheme here
 * documents the contract from REST API Specification section 4.1; it does not implement it.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI posOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("POS Management System API")
                                .version("v1")
                                .description(
                                        "Integrated POS, Inventory & Business Management System."
                                            + " Contract source: REST API Specification v1.0."))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Access token issued by"
                                                            + " POST /api/v1/auth/login.")));
    }
}
