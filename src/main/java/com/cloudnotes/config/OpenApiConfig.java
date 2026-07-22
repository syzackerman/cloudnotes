package com.cloudnotes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI cloudNotesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CloudNotes API")
                        .version("1.0.0")
                        .description(
                                """
                                CloudNotes is a secure, multi-user note management API with JWT authentication,
                                ownership-protected notes, tags, favorites, soft deletion, and private S3-backed attachments.

                                API routes are currently served under `/api` and documented as version 1.
                                Register or log in, copy the returned JWT, click Swagger UI's Authorize button,
                                and enter the token as a Bearer JWT.
                                """)
                        .contact(new Contact().name("CloudNotes Maintainer").email("maintainer@example.com"))
                        .license(new License().name("License placeholder").url("https://example.com/license")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
