package com.reviewsystem.infrastructure.monitoring;

import com.reviewsystem.application.service.ReviewQueryService;
import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.repository.ProcessedFileRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Central component for collecting and managing application metrics */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsCollector {

  private final MeterRegistry meterRegistry;
  private final ReviewQueryService reviewQueryService;
  private final ProcessedFileRepository processedFileRepository;

  // Atomic counters for real-time metrics
  private final AtomicInteger activeProcessingCount = new AtomicInteger(0);
  private final AtomicInteger queueSize = new AtomicInteger(0);
  private final Map<String, AtomicLong> customGauges = new ConcurrentHashMap<>();

  // Memory management for monitoring
  private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

  /** Record a processed review with provider and status */
  public void recordReviewProcessed(ProviderType provider, ProcessingStatus status) {
    recordReviewProcessed(provider, status, 1);
  }

  /** Record multiple processed reviews with provider and status */
  public void recordReviewProcessed(ProviderType provider, ProcessingStatus status, int count) {
    Counter.builder("reviews.processed.total")
        .tag("provider", provider.name())
        .tag("status", status.name())
        .description("Total number of reviews processed")
        .register(meterRegistry)
        .increment(count);

    log.debug(
        "Recorded {} processed reviews for provider: {}, status: {}", count, provider, status);
  }

  /** Record a processed file with provider and status */
  public void recordFileProcessed(ProviderType provider, ProcessingStatus status) {
    Counter.builder("files.processed.total")
        .tag("provider", provider.name())
        .tag("status", status.name())
        .description("Total number of files processed")
        .register(meterRegistry)
        .increment();

    log.debug("Recorded processed file for provider: {}, status: {}", provider, status);
  }

  /** Record processing duration for a provider */
  public void recordProcessingDuration(ProviderType provider, Duration duration) {
    Timer.builder("processing.duration")
        .tag("provider", provider.name())
        .description("Processing duration by provider")
        .register(meterRegistry)
        .record(duration.toMillis(), TimeUnit.MILLISECONDS);

    log.debug(
        "Recorded processing duration: {} ms for provider: {}", duration.toMillis(), provider);
  }

  /** Start a processing timer */
  public Timer.Sample startProcessingTimer() {
    return Timer.start(meterRegistry);
  }

  /** Stop a processing timer and record the duration */
  public void stopProcessingTimer(Timer.Sample sample, ProviderType provider) {
    sample.stop(
        Timer.builder("processing.duration")
            .tag("provider", provider.name())
            .description("Processing duration by provider")
            .register(meterRegistry));
  }

  /** Record database query execution time */
  public void recordDatabaseQuery(String queryName, Duration duration) {
    Timer.builder("database.query.duration")
        .tag("query", queryName)
        .description("Database query execution time")
        .register(meterRegistry)
        .record(duration.toMillis(), TimeUnit.MILLISECONDS);

    log.debug(
        "Recorded database query duration: {} ms for query: {}", duration.toMillis(), queryName);
  }

  /** Record S3 operation success/failure */
  public void recordS3Operation(String operation, boolean success) {
    Counter.builder("s3.operations.total")
        .tag("operation", operation)
        .tag("success", String.valueOf(success))
        .description("Total S3 operations")
        .register(meterRegistry)
        .increment();

    log.debug("Recorded S3 operation: {}, success: {}", operation, success);
  }

  /** Record validation errors by provider and error type */
  public void recordValidationError(ProviderType provider, String errorType) {
    Counter.builder("validation.errors.total")
        .tag("provider", provider.name())
        .tag("error_type", errorType)
        .description("Total validation errors")
        .register(meterRegistry)
        .increment();

    log.debug("Recorded validation error for provider: {}, error type: {}", provider, errorType);
  }

  /** Update active processing count gauge */
  public void updateActiveProcessingCount(int count) {
    activeProcessingCount.set(count);
    meterRegistry.gauge(
        "processing.active.count", this, MetricsCollector::getActiveProcessingCount);

    log.debug("Updated active processing count: {}", count);
  }

  /** Update queue size gauge */
  public void updateQueueSize(int size) {
    queueSize.set(size);
    meterRegistry.gauge("processing.queue.size", this, MetricsCollector::getQueueSize);

    log.debug("Updated queue size: {}", size);
  }

  /** Record API request metrics */
  public void recordApiRequest(String endpoint, String method, int statusCode) {
    Counter.builder("api.requests.total")
        .tag("endpoint", endpoint)
        .tag("method", method)
        .tag("status", String.valueOf(statusCode))
        .description("Total API requests")
        .register(meterRegistry)
        .increment();

    log.debug("Recorded API request: {} {}, status: {}", method, endpoint, statusCode);
  }

  /** Record API request duration */
  public void recordApiRequestDuration(String endpoint, String method, Duration duration) {
    Timer.builder("api.request.duration")
        .tag("endpoint", endpoint)
        .tag("method", method)
        .description("API request duration")
        .register(meterRegistry)
        .record(duration.toMillis(), TimeUnit.MILLISECONDS);

    log.debug(
        "Recorded API request duration: {} ms for {} {}", duration.toMillis(), method, endpoint);
  }

  /** Record custom metric counter */
  public void recordCustomMetric(String metricName, String tagKey, String tagValue) {
    Counter.builder(metricName).tag(tagKey, tagValue).register(meterRegistry).increment();

    log.debug("Recorded custom metric: {} with tag {}={}", metricName, tagKey, tagValue);
  }

  /** Record custom gauge value */
  public void recordCustomGauge(String gaugeName, double value) {
    customGauges.computeIfAbsent(gaugeName, k -> new AtomicLong()).set((long) value);
    meterRegistry.gauge(gaugeName, this, obj -> customGauges.get(gaugeName).get());

    log.debug("Recorded custom gauge: {} = {}", gaugeName, value);
  }

  /** Get comprehensive system metrics */
  public Map<String, Object> getSystemMetrics() {
    Map<String, Object> metrics = new HashMap<>();

    try {
      // Review metrics
      metrics.put("totalReviews", reviewQueryService.getTotalReviewCount());
      metrics.put(
          "reviewsProcessedLast24Hours",
          reviewQueryService.getReviewCountByDateRange(
              LocalDate.now().minusDays(1), LocalDate.now()));
      metrics.put("averageRating", reviewQueryService.getAverageRating());

      // File processing metrics
      metrics.put("filesProcessedToday", processedFileRepository.countProcessedFilesToday());
      metrics.put("failedFilesToday", processedFileRepository.countFailedFilesToday());

      // Active processing metrics
      metrics.put("activeProcessingCount", activeProcessingCount.get());
      metrics.put("queueSize", queueSize.get());

      // Timestamp
      metrics.put("timestamp", LocalDateTime.now());

      log.debug("Generated system metrics: {} items", metrics.size());

    } catch (Exception e) {
      log.error("Failed to collect system metrics", e);
      metrics.put("error", "Failed to collect metrics: " + e.getMessage());
    }

    return metrics;
  }

  /** Get provider-specific metrics */
  public Map<String, Object> getProviderMetrics(ProviderType provider) {
    Map<String, Object> metrics = new HashMap<>();

    try {
      metrics.put("provider", provider.name());
      metrics.put("totalReviews", reviewQueryService.getReviewCountByProvider(provider));
      metrics.put("averageRating", reviewQueryService.getAverageRatingByProvider(provider));
      metrics.put(
          "processedFiles",
          processedFileRepository.countByProviderAndProcessingStatus(
              provider.name(), ProcessingStatus.COMPLETED));
      metrics.put(
          "failedFiles",
          processedFileRepository.countByProviderAndProcessingStatus(
              provider.name(), ProcessingStatus.FAILED));
      metrics.put("timestamp", LocalDateTime.now());

      log.debug("Generated provider metrics for {}: {} items", provider, metrics.size());

    } catch (Exception e) {
      log.error("Failed to collect provider metrics for {}", provider, e);
      metrics.put("error", "Failed to collect metrics: " + e.getMessage());
    }

    return metrics;
  }

  /** Get memory usage metrics */
  public Map<String, Object> getMemoryMetrics() {
    Map<String, Object> metrics = new HashMap<>();

    try {
      var heapMemory = memoryBean.getHeapMemoryUsage();
      var nonHeapMemory = memoryBean.getNonHeapMemoryUsage();

      long heapUsed = heapMemory.getUsed();
      long heapMax = heapMemory.getMax();
      double heapUsagePercent = heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0;

      metrics.put("heapUsed", heapUsed);
      metrics.put("heapMax", heapMax);
      metrics.put("heapUsagePercent", Math.round(heapUsagePercent * 100.0) / 100.0);
      metrics.put("nonHeapUsed", nonHeapMemory.getUsed());
      metrics.put("timestamp", LocalDateTime.now());

      log.debug("Generated memory metrics - heap usage: {}%", heapUsagePercent);

    } catch (Exception e) {
      log.error("Failed to collect memory metrics", e);
      metrics.put("error", "Failed to collect memory metrics: " + e.getMessage());
    }

    return metrics;
  }

  /** Get all metrics combined */
  public Map<String, Object> getAllMetrics() {
    Map<String, Object> allMetrics = new HashMap<>();

    try {
      // System metrics
      allMetrics.put("system", getSystemMetrics());

      // Memory metrics
      allMetrics.put("memory", getMemoryMetrics());

      // Provider-specific metrics
      Map<String, Object> providerMetrics = new HashMap<>();
      for (ProviderType provider : ProviderType.values()) {
        providerMetrics.put(provider.name(), getProviderMetrics(provider));
      }
      allMetrics.put("providers", providerMetrics);

      // Custom gauges
      if (!customGauges.isEmpty()) {
        Map<String, Long> customMetrics = new HashMap<>();
        customGauges.forEach((key, value) -> customMetrics.put(key, value.get()));
        allMetrics.put("custom", customMetrics);
      }

      allMetrics.put("timestamp", LocalDateTime.now());

      log.debug("Generated comprehensive metrics with {} top-level categories", allMetrics.size());

    } catch (Exception e) {
      log.error("Failed to collect comprehensive metrics", e);
      allMetrics.put("error", "Failed to collect metrics: " + e.getMessage());
    }

    return allMetrics;
  }

  /** Clear all custom metrics (useful for testing or cleanup) */
  public void clearCustomMetrics() {
    customGauges.clear();
    log.info("Cleared all custom metrics");
  }

  /** Reset counters (useful for testing) */
  public void resetCounters() {
    activeProcessingCount.set(0);
    queueSize.set(0);
    customGauges.clear();
    log.info("Reset all metric counters");
  }

  // Getter methods for gauge callbacks
  private double getActiveProcessingCount() {
    return activeProcessingCount.get();
  }

  private double getQueueSize() {
    return queueSize.get();
  }
}
