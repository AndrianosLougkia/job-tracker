package com.jobtracker.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI spec to include a Bearer token security scheme.
 * After logging in via /auth/login, paste the token into the Authorize dialog in Swagger UI.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
    title = "Job Tracker API",
    version = "0.1.0",
    description = "AI-powered job application tracker"
))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
