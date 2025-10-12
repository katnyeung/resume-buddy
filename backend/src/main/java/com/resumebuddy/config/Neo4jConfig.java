package com.resumebuddy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j Configuration for Spring Data Neo4j.
 *
 * Spring Boot auto-configures Neo4j based on application.yml:
 * - spring.neo4j.uri
 * - spring.neo4j.authentication.username
 * - spring.neo4j.authentication.password
 * - spring.data.neo4j.database
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "com.resumebuddy.repository")
public class Neo4jConfig {
    // Spring Boot handles Neo4j driver creation automatically
    // Configuration is loaded from application.yml
}
