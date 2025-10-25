package com.resumebuddy.jobsearch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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
                        .description("AI-powered job search and matching service with vector similarity search")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Resume Buddy")
                                .email("support@resumebuddy.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8085")
                                .description("Local Development Server")
                ));
    }
}
