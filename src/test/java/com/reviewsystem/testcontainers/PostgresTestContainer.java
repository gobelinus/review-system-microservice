package com.reviewsystem.testcontainers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TestContainer configuration for PostgreSQL database integration tests
 */
@TestConfiguration
@Testcontainers
public class PostgresTestContainer {

    @Container
    public static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("reviewsystem_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withInitScript("db/test-init.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Bean
    public PostgreSQLContainer<?> postgreSQLContainer() {
        return postgresql;
    }
}