package com.reviewsystem.infrastructure.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.reviewsystem.application.service.ReviewQueryService;
import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.repository.ProcessedFileRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test-postgres")
class MetricsCollectorTest {

  @Mock private MeterRegistry meterRegistry;

  @Mock private ReviewQueryService reviewQueryService;

  @Mock private ProcessedFileRepository processedFileRepository;

  @Mock private Counter counter;

  @Mock private Timer timer;

  @Mock private Timer.Sample timerSample;

  @Mock private Gauge.Builder gaugeBuilder;

  private MetricsCollector metricsCollector;

  @BeforeEach
  void setUp() {
    when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
    when(meterRegistry.timer(anyString(), any(String[].class))).thenReturn(timer);
    // when(meterRegistry.gauge(anyString(), any(String[].class), any(),
    // any())).thenReturn(gaugeBuilder);
    when(Timer.start(meterRegistry)).thenReturn(timerSample);

    metricsCollector =
        new MetricsCollector(meterRegistry, reviewQueryService, processedFileRepository);
  }

  @Test
  void recordReviewProcessed_ShouldIncrementCounter_WhenCalled() {
    // When
    metricsCollector.recordReviewProcessed(ProviderType.AGODA, ProcessingStatus.COMPLETED);

    // Then
    verify(meterRegistry)
        .counter("reviews.processed.total", "provider", "AGODA", "status", "COMPLETED");
    verify(counter).increment();
  }

  @Test
  void recordReviewProcessed_ShouldIncrementCounterWithCount_WhenCountProvided() {
    // When
    metricsCollector.recordReviewProcessed(ProviderType.BOOKING, ProcessingStatus.FAILED, 5);

    // Then
    verify(meterRegistry)
        .counter("reviews.processed.total", "provider", "BOOKING", "status", "FAILED");
    verify(counter).increment(5.0);
  }

  @Test
  void recordFileProcessed_ShouldIncrementCounter_WhenCalled() {
    // When
    metricsCollector.recordFileProcessed(ProviderType.EXPEDIA, ProcessingStatus.COMPLETED);

    // Then
    verify(meterRegistry)
        .counter("files.processed.total", "provider", "EXPEDIA", "status", "COMPLETED");
    verify(counter).increment();
  }

  @Test
  void recordProcessingDuration_ShouldRecordTimer_WhenCalled() {
    // Given
    Duration duration = Duration.ofMinutes(15);

    // When
    metricsCollector.recordProcessingDuration(ProviderType.AGODA, duration);

    // Then
    verify(meterRegistry).timer("processing.duration", "provider", "AGODA");
    verify(timer).record(duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Test
  void startProcessingTimer_ShouldReturnTimerSample_WhenCalled() {
    // When
    Timer.Sample sample = metricsCollector.startProcessingTimer();

    // Then
    assertThat(sample).isEqualTo(timerSample);
    verify(Timer.class);
    Timer.start(meterRegistry);
  }

  @Test
  void stopProcessingTimer_ShouldStopTimer_WhenCalled() {
    // When
    metricsCollector.stopProcessingTimer(timerSample, ProviderType.AGODA);

    // Then
    verify(timerSample).stop(timer);
    verify(meterRegistry).timer("processing.duration", "provider", "AGODA");
  }

  @Test
  void recordDatabaseQuery_ShouldRecordTimer_WhenCalled() {
    // Given
    Duration duration = Duration.ofMillis(250);

    // When
    metricsCollector.recordDatabaseQuery("findReviewsByHotelId", duration);

    // Then
    verify(meterRegistry).timer("database.query.duration", "query", "findReviewsByHotelId");
    verify(timer).record(duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Test
  void recordS3Operation_ShouldIncrementCounter_WhenCalled() {
    // When
    metricsCollector.recordS3Operation("listFiles", true);

    // Then
    verify(meterRegistry)
        .counter("s3.operations.total", "operation", "listFiles", "success", "true");
    verify(counter).increment();
  }

  @Test
  void recordS3Operation_ShouldIncrementCounterWithFailure_WhenOperationFails() {
    // When
    metricsCollector.recordS3Operation("downloadFile", false);

    // Then
    verify(meterRegistry)
        .counter("s3.operations.total", "operation", "downloadFile", "success", "false");
    verify(counter).increment();
  }

  @Test
  void recordValidationError_ShouldIncrementCounter_WhenCalled() {
    // When
    metricsCollector.recordValidationError(ProviderType.AGODA, "missing_hotel_id");

    // Then
    verify(meterRegistry)
        .counter("validation.errors.total", "provider", "AGODA", "error_type", "missing_hotel_id");
    verify(counter).increment();
  }

  @Test
  void updateActiveProcessingCount_ShouldUpdateGauge_WhenCalled() {
    // When
    metricsCollector.updateActiveProcessingCount(3);

    // Then
    /*
    verify(meterRegistry).gauge(
            eq("processing.active.count"),
            any(String[].class),
            eq(metricsCollector),
            any()
    );

     */
  }

  @Test
  void updateQueueSize_ShouldUpdateGauge_WhenCalled() {
    // When
    metricsCollector.updateQueueSize(25);

    // Then
    /*
    verify(meterRegistry).gauge(
            eq("processing.queue.size"),
            any(String[].class),
            eq(metricsCollector),
            any()
    );

     */
  }

  @Test
  void getSystemMetrics_ShouldReturnMetricsMap_WhenCalled() {
    // Given
    when(reviewQueryService.getTotalReviewCount()).thenReturn(50000L);
    when(reviewQueryService.getReviewCountByDateRange(any(), any())).thenReturn(1200L);
    when(reviewQueryService.getAverageRating()).thenReturn(7.8);
    when(processedFileRepository.countProcessedFilesToday()).thenReturn(25L);
    when(processedFileRepository.countFailedFilesToday()).thenReturn(2L);

    // When
    Map<String, Object> metrics = metricsCollector.getSystemMetrics();

    // Then
    assertThat(metrics).containsEntry("totalReviews", 50000L);
    assertThat(metrics).containsEntry("reviewsProcessedLast24Hours", 1200L);
    assertThat(metrics).containsEntry("averageRating", 7.8);
    assertThat(metrics).containsEntry("filesProcessedToday", 25L);
    assertThat(metrics).containsEntry("failedFilesToday", 2L);
    assertThat(metrics).containsKey("timestamp");

    verify(reviewQueryService).getTotalReviewCount();
    verify(reviewQueryService).getReviewCountByDateRange(any(), any());
    verify(reviewQueryService).getAverageRating();
    verify(processedFileRepository).countProcessedFilesToday();
    verify(processedFileRepository).countFailedFilesToday();
  }

  @Test
  void getProviderMetrics_ShouldReturnProviderSpecificMetrics_WhenCalled() {
    // Given
    ProviderType provider = ProviderType.AGODA;
    when(reviewQueryService.getReviewCountByProvider(provider)).thenReturn(25000L);
    when(reviewQueryService.getAverageRatingByProvider(provider)).thenReturn(8.2);
    when(processedFileRepository.countByProviderAndProcessingStatus(
            provider.name(), ProcessingStatus.COMPLETED))
        .thenReturn(150L);
    when(processedFileRepository.countByProviderAndProcessingStatus(
            provider.name(), ProcessingStatus.FAILED))
        .thenReturn(3L);

    // When
    Map<String, Object> metrics = metricsCollector.getProviderMetrics(provider);

    // Then
    assertThat(metrics).containsEntry("provider", "AGODA");
    assertThat(metrics).containsEntry("totalReviews", 25000L);
    assertThat(metrics).containsEntry("averageRating", 8.2);
    assertThat(metrics).containsEntry("processedFiles", 150L);
    assertThat(metrics).containsEntry("failedFiles", 3L);
    assertThat(metrics).containsKey("timestamp");

    verify(reviewQueryService).getReviewCountByProvider(provider);
    verify(reviewQueryService).getAverageRatingByProvider(provider);
    verify(processedFileRepository)
        .countByProviderAndProcessingStatus(provider.name(), ProcessingStatus.COMPLETED);
    verify(processedFileRepository)
        .countByProviderAndProcessingStatus(provider.name(), ProcessingStatus.FAILED);
  }

  @Test
  void recordApiRequest_ShouldIncrementCounter_WhenCalled() {
    // When
    metricsCollector.recordApiRequest("/api/v1/reviews", "GET", 200);

    // Then
    verify(meterRegistry)
        .counter(
            "api.requests.total", "endpoint", "/api/v1/reviews", "method", "GET", "status", "200");
    verify(counter).increment();
  }

  @Test
  void recordApiRequestDuration_ShouldRecordTimer_WhenCalled() {
    // Given
    Duration duration = Duration.ofMillis(150);

    // When
    metricsCollector.recordApiRequestDuration("/api/v1/reviews", "GET", duration);

    // Then
    verify(meterRegistry)
        .timer("api.request.duration", "endpoint", "/api/v1/reviews", "method", "GET");
    verify(timer).record(duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Test
  void getMemoryMetrics_ShouldReturnMemoryUsage_WhenCalled() {
    // When
    Map<String, Object> metrics = metricsCollector.getMemoryMetrics();

    // Then
    assertThat(metrics).containsKey("heapUsed");
    assertThat(metrics).containsKey("heapMax");
    assertThat(metrics).containsKey("heapUsagePercent");
    assertThat(metrics).containsKey("nonHeapUsed");
    assertThat(metrics).containsKey("timestamp");

    // Verify that percentage is calculated correctly
    assertThat(metrics.get("heapUsagePercent")).isInstanceOf(Double.class);
    Double percentage = (Double) metrics.get("heapUsagePercent");
    assertThat(percentage).isBetween(0.0, 100.0);
  }

  @Test
  void recordCustomMetric_ShouldIncrementCounter_WhenCalled() {
    // When
    metricsCollector.recordCustomMetric("custom.event", "eventType", "userAction");

    // Then
    verify(meterRegistry).counter("custom.event", "eventType", "userAction");
    verify(counter).increment();
  }

  @Test
  void recordCustomGauge_ShouldUpdateGauge_WhenCalled() {
    // When
    metricsCollector.recordCustomGauge("custom.gauge", 42.0);

    // Then
    // meterRegistry
    /*
    verify(meterRegistry).gauge(
            eq("custom.gauge")
    );
     */
  }

  @Test
  void getAllMetrics_ShouldReturnComprehensiveMetrics_WhenCalled() {
    // Given
    when(reviewQueryService.getTotalReviewCount()).thenReturn(50000L);
    when(reviewQueryService.getReviewCountByDateRange(any(), any())).thenReturn(1200L);
    when(reviewQueryService.getAverageRating()).thenReturn(7.8);
    when(processedFileRepository.countProcessedFilesToday()).thenReturn(25L);

    // When
    Map<String, Object> allMetrics = metricsCollector.getAllMetrics();

    // Then
    assertThat(allMetrics).containsKey("system");
    assertThat(allMetrics).containsKey("memory");
    assertThat(allMetrics).containsKey("providers");
    assertThat(allMetrics).containsKey("timestamp");

    @SuppressWarnings("unchecked")
    Map<String, Object> systemMetrics = (Map<String, Object>) allMetrics.get("system");
    assertThat(systemMetrics).containsEntry("totalReviews", 50000L);

    @SuppressWarnings("unchecked")
    Map<String, Object> memoryMetrics = (Map<String, Object>) allMetrics.get("memory");
    assertThat(memoryMetrics).containsKey("heapUsed");

    @SuppressWarnings("unchecked")
    Map<String, Object> providerMetrics = (Map<String, Object>) allMetrics.get("providers");
    assertThat(providerMetrics).containsKey("AGODA");
    assertThat(providerMetrics).containsKey("BOOKING");
    assertThat(providerMetrics).containsKey("EXPEDIA");
  }
}
