package com.resumebuddy.jobsearch.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j Configuration for Job Search Service
 * Provides Neo4j Driver bean for indexing job listings
 */
@Configuration
@Slf4j
public class Neo4jConfig {

    @Value("${spring.neo4j.uri}")
    private String uri;

    @Value("${spring.neo4j.authentication.username}")
    private String username;

    @Value("${spring.neo4j.authentication.password}")
    private String password;

    @Bean
    public Driver neo4jDriver() {
        log.info("Initializing Neo4j driver - URI: {}, Username: {}", uri, username);
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}
