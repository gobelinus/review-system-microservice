package com.reviewsystem.infrastructure.monitoring;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Database Health Indicator to check database connectivity and performance */
@Component("database")
@RequiredArgsConstructor
@Slf4j
public class DatabaseHealthIndicator implements HealthIndicator {

  private final DataSource dataSource;

  @Override
  public Health health() {
    try {
      log.debug("Checking database health status");

      try (Connection connection = dataSource.getConnection()) {
        boolean isValid = connection.isValid(5); // 5 second timeout

        Health.Builder healthBuilder = isValid ? Health.up() : Health.down();

        healthBuilder.withDetail("lastChecked", LocalDateTime.now());

        if (isValid) {
          // Get database metadata
          try {
            var metaData = connection.getMetaData();
            healthBuilder
                .withDetail("database", metaData.getDatabaseProductName())
                .withDetail("version", metaData.getDatabaseProductVersion())
                .withDetail("url", metaData.getURL())
                .withDetail("status", "Connected");
            log.debug("Database health check passed");
          } catch (SQLException e) {
            log.warn("Could not retrieve database metadata", e);
            healthBuilder.withDetail("metadataError", e.getMessage());
          }
        } else {
          healthBuilder
              .withDetail("error", "Database connection is not valid")
              .withDetail("status", "Disconnected");
          log.warn("Database health check failed - connection is not valid");
        }

        return healthBuilder.build();
      }

    } catch (SQLException e) {
      log.error("Database health check failed with SQL exception", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("lastChecked", LocalDateTime.now())
          .withDetail("status", "Error")
          .build();
    } catch (Exception e) {
      log.error("Database health check failed with unexpected exception", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("lastChecked", LocalDateTime.now())
          .withDetail("status", "Error")
          .build();
    }
  }
}
