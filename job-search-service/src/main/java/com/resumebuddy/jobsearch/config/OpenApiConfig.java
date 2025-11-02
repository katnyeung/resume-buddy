package com.resumebuddy.jobsearch.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jobSearchServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Job Search Service API")
                        .description("AI-powered job search and matching service with vector similarity search. " +
                                   "Admin endpoints require ROLE_ADMIN in JWT token.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Resume Buddy")
                                .email("support@resumebuddy.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("https://resumebuddy.cv/")
                                .description("Production"),
                        new Server()
                                .url("http://localhost:8085")
                                .description("Local Development Server")
                ))
                // Add JWT Bearer token authentication
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("JWT token from login. Click 'Authorize', paste your token (without 'Bearer ' prefix), then click 'Authorize' button.")));
    }
}
