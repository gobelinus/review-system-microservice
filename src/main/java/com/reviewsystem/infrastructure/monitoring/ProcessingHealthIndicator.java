package com.reviewsystem.infrastructure.monitoring;

import com.reviewsystem.application.service.ProcessingOrchestrationService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Processing Health Indicator to check the status of review processing operations */
@Component("processing")
@RequiredArgsConstructor
@Slf4j
public class ProcessingHealthIndicator implements HealthIndicator {

  private final ProcessingOrchestrationService processingOrchestrationService;

  // Thresholds for health determination
  private static final int MAX_QUEUE_SIZE = 500;
  private static final double MAX_FAILURE_RATE = 0.1; // 10%
  private static final int MIN_TOTAL_PROCESSES_FOR_RATE_CALC = 10;

  @Override
  public Health health() {
    try {
      log.debug("Checking processing health status");

      Map<String, Object> processingStatus =
          processingOrchestrationService.getProcessingHealthStatus();

      String status = (String) processingStatus.get("status");
      Integer activeProcesses = (Integer) processingStatus.get("activeProcesses");
      Integer totalProcessedToday = (Integer) processingStatus.get("totalProcessedToday");
      Integer failedProcessesToday = (Integer) processingStatus.get("failedProcessesToday");
      Integer queueSize = (Integer) processingStatus.get("queueSize");

      Health.Builder healthBuilder =
          determineHealthStatus(status, totalProcessedToday, failedProcessesToday, queueSize);

      // Add all processing details
      healthBuilder
          .withDetail("status", status)
          .withDetail("activeProcesses", activeProcesses)
          .withDetail("totalProcessedToday", totalProcessedToday)
          .withDetail("failedProcessesToday", failedProcessesToday)
          .withDetail("queueSize", queueSize)
          .withDetail("lastChecked", LocalDateTime.now());

      // Add additional details if available
      if (processingStatus.containsKey("lastProcessing")) {
        healthBuilder.withDetail("lastProcessing", processingStatus.get("lastProcessing"));
      }

      if (totalProcessedToday != null && totalProcessedToday > 0) {
        double successRate =
            totalProcessedToday > 0
                ? (double) (totalProcessedToday - failedProcessesToday) / totalProcessedToday
                : 1.0;
        healthBuilder.withDetail(
            "successRate", Math.round(successRate * 10000.0) / 100.0); // Percentage with 2 decimals
      }

      log.debug("Processing health check completed - status: {}", status);
      return healthBuilder.build();

    } catch (Exception e) {
      log.error("Processing health check failed with exception", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("lastChecked", LocalDateTime.now())
          .withDetail("status", "Error")
          .build();
    }
  }

  private Health.Builder determineHealthStatus(
      String status, Integer totalProcessedToday, Integer failedProcessesToday, Integer queueSize) {
    // Check if processing is administratively paused
    if ("PAUSED".equalsIgnoreCase(status)) {
      return Health.outOfService().withDetail("reason", "Processing is administratively paused");
    }

    // Check queue size
    if (queueSize != null && queueSize > MAX_QUEUE_SIZE) {
      return Health.down().withDetail("reason", "Processing queue is full");
    }

    // Check failure rate
    if (totalProcessedToday != null
        && failedProcessesToday != null
        && totalProcessedToday >= MIN_TOTAL_PROCESSES_FOR_RATE_CALC) {
      double failureRate = (double) failedProcessesToday / totalProcessedToday;
      if (failureRate > MAX_FAILURE_RATE) {
        return Health.down()
            .withDetail("reason", "High failure rate detected")
            .withDetail("failureRate", Math.round(failureRate * 10000.0) / 100.0);
      }
    }

    // Check if there are error conditions
    if ("ERROR".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
      return Health.down().withDetail("reason", "Processing is in error state");
    }

    // All checks passed
    return Health.up();
  }
}
