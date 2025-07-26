package com.reviewsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Database configuration for JPA and auditing
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.reviewsystem.infrastructure.persistence.jpa")
@EnableJpaAuditing
@EnableTransactionManagement
public class DatabaseConfig {

    // Additional database configuration beans can be added here
    // For example: DataSource, TransactionManager, etc.
}