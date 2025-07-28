package com.reviewsystem.infrastructure.monitoring;

import com.reviewsystem.infrastructure.aws.S3Service;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** S3 Health Indicator to check AWS S3 connectivity and bucket accessibility */
@Component("s3")
@RequiredArgsConstructor
@Slf4j
public class S3HealthIndicator implements HealthIndicator {

  private final S3Service s3Service;

  @Override
  public Health health() {
    try {
      log.debug("Checking S3 health status");

      boolean isHealthy = s3Service.checkConnectivity();
      String bucketName = s3Service.getBucketName();

      Health.Builder healthBuilder = isHealthy ? Health.up() : Health.down();

      healthBuilder.withDetail("bucket", bucketName).withDetail("lastChecked", LocalDateTime.now());

      if (isHealthy) {
        healthBuilder.withDetail("region", s3Service.getRegion()).withDetail("status", "Connected");
        log.debug("S3 health check passed");
      } else {
        healthBuilder
            .withDetail("error", "S3 connectivity check failed")
            .withDetail("status", "Disconnected");
        log.warn("S3 health check failed - connectivity issue");
      }

      return healthBuilder.build();

    } catch (Exception e) {
      log.error("S3 health check failed with exception", e);
      return Health.down()
          .withDetail("bucket", s3Service.getBucketName())
          .withDetail("error", e.getMessage())
          .withDetail("lastChecked", LocalDateTime.now())
          .withDetail("status", "Error")
          .build();
    }
  }
}
