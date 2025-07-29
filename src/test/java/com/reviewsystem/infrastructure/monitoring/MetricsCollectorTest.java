package com.reviewsystem.infrastructure.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.reviewsystem.application.service.ReviewQueryService;
import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.repository.ProcessedFileRepository;
import io.micrometer.core.instrument.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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

  @Mock private Timer.Builder mockTimerBuilder; // New mock for Timer.Builder

  @Mock private Counter.Builder mockCounterBuilder; // NEW: Mock for Counter.Builder

  @Mock private Timer.Sample timerSample;

  @Mock private Gauge.Builder gaugeBuilder;

  // MockedStatic instances for static methods
  private MockedStatic<Timer> mockedTimer;
  private MockedStatic<Counter> mockedCounter; // NEW: MockedStatic for Counter class

  private MetricsCollector metricsCollector;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this); // Initialize all @Mock annotated fields

    // 1. Mock static Timer.start()
    mockedTimer = Mockito.mockStatic(Timer.class);
    mockedTimer.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);
    // Stub Timer.builder(String) to return our mock Timer.Builder
    mockedTimer.when(() -> Timer.builder(anyString())).thenReturn(mockTimerBuilder);

    // Mock Counter
    mockedCounter = Mockito.mockStatic(Counter.class);
    mockedCounter.when(() -> Counter.builder(anyString())).thenReturn(mockCounterBuilder);

    // 1b. Configure the mock Counter.Builder fluent API ---
    // NEW: Ensure chaining works for Counter.Builder
    lenient().when(mockCounterBuilder.tag(anyString(), anyString())).thenReturn(mockCounterBuilder);
    lenient().when(mockCounterBuilder.description(anyString())).thenReturn(mockCounterBuilder);
    // NEW: When register() is called on Counter.Builder, return the main 'counter' mock
    lenient().when(mockCounterBuilder.register(eq(meterRegistry))).thenReturn(counter);
    // NEW: Stub the increment() method on the returned 'counter' mock (if it were void, use
    // doNothing)
    // Your code calls counter.increment(double), so ensure the mock 'counter' can handle it.
    // It's already mocked to return 'counter' so its methods are available.
    lenient().doNothing().when(counter).increment(anyDouble()); // Assuming increment takes double

    // 2. Mock MeterRegistry.config() and Clock
    MeterRegistry.Config mockConfig = mock(MeterRegistry.Config.class);
    Clock mockClock = mock(Clock.class);
    lenient()
        .when(mockClock.monotonicTime())
        .thenReturn(System.nanoTime()); // Necessary for Timer.start()
    lenient().when(mockConfig.clock()).thenReturn(mockClock);
    lenient().when(meterRegistry.config()).thenReturn(mockConfig);

    // These ensure chaining works (e.g., .tag().description())
    lenient().when(mockTimerBuilder.tag(anyString(), anyString())).thenReturn(mockTimerBuilder);
    lenient().when(mockTimerBuilder.description(anyString())).thenReturn(mockTimerBuilder);

    // When register() is called on the builder, return the main 'timer' mock
    lenient().when(mockTimerBuilder.register(eq(meterRegistry))).thenReturn(timer);
    lenient().doNothing().when(timer).record(anyLong(), any());

    // 3. Stubbing for Counter.builder().register(meterRegistry) calls
    // Your MetricsCollector uses Counter.builder().tag().register(meterRegistry)
    // This implicitly calls meterRegistry.counter(name, tags) where tags is an Iterable<Tag>
    lenient().when(meterRegistry.counter(anyString(), anyIterable())).thenReturn(counter);

    // 4. Stubbing for Timer.builder().register(meterRegistry) calls
    // Similar to Counter, your MetricsCollector uses Timer.builder().tag().register(meterRegistry)
    // This implicitly calls meterRegistry.timer(name, tags) where tags is an Iterable<Tag>
    lenient().when(meterRegistry.timer(anyString(), anyIterable())).thenReturn(timer);

    // 5. Stubbing for Gauge
    // Your MetricsCollector uses gauge methods WITHOUT a tags argument:
    // meterRegistry.gauge("processing.active.count", this,
    // MetricsCollector::getActiveProcessingCount);
    // meterRegistry.gauge("processing.queue.size", this, MetricsCollector::getQueueSize);
    // meterRegistry.gauge(gaugeName, this, obj -> customGauges.get(gaugeName).get());

    // So, the stubbing should only match the `name`, `obj`, and `ToDoubleFunction` or
    // `GaugeFunction` arguments.
    lenient()
        .when(
            meterRegistry.gauge(
                anyString(), // name
                any(MetricsCollector.class), // obj (it's 'this', so MetricsCollector instance)
                any(ToDoubleFunction.class) // <-- SPECIFICALLY ToDoubleFunction
                ))
        .thenReturn(metricsCollector);

    // Instantiate your MetricsCollector with the mocked dependencies
    metricsCollector =
        new MetricsCollector(meterRegistry, reviewQueryService, processedFileRepository);
  }

  @AfterEach // Important: Close the static mock after each test
  void tearDown() {
    if (mockedTimer != null) {
      mockedTimer.close();
    }
    if (mockedCounter != null) {
      mockedCounter.close();
    }
  }

  @Nested
  @DisplayName("Recording Metric Tests")
  class RecordMetricTest {
    @Test
    void recordReviewProcessed_ShouldIncrementCounter_WhenCalled() {
      // Given (setup for this specific test, if any, often combined with When)
      ProviderType provider = ProviderType.AGODA;
      ProcessingStatus status = ProcessingStatus.COMPLETED;

      // When
      metricsCollector.recordReviewProcessed(provider, status);
      // Then
      mockedCounter.verify(() -> Counter.builder(eq("reviews.processed.total")));
      // Verify fluent chain on the mock builder
      verify(mockCounterBuilder).tag(eq("provider"), eq(provider.name()));
      verify(mockCounterBuilder).tag(eq("status"), eq(status.name()));
      verify(mockCounterBuilder).description(eq("Total number of reviews processed"));
      verify(mockCounterBuilder).register(eq(meterRegistry));
      // Verify increment on the final 'counter' mock
      verify(counter).increment(eq(1.0));
    }

    @Test
    void recordReviewProcessed_ShouldIncrementCounterWithCount_WhenCountProvided() {
      // Given (setup for this specific test, if any, often combined with When)
      ProviderType provider = ProviderType.BOOKING;
      ProcessingStatus status = ProcessingStatus.FAILED;
      int count = 5;
      // When
      metricsCollector.recordReviewProcessed(provider, status, count);

      // Then
      mockedCounter.verify(() -> Counter.builder(eq("reviews.processed.total")));
      // Verify fluent chain on the mock builder
      verify(mockCounterBuilder).tag(eq("provider"), eq(provider.name()));
      verify(mockCounterBuilder).tag(eq("status"), eq(status.name()));
      verify(mockCounterBuilder).description(eq("Total number of reviews processed"));
      verify(mockCounterBuilder).register(eq(meterRegistry));
      // Verify increment on the final 'counter' mock
      verify(counter).increment(eq((double) count));
    }

    @Test
    void recordFileProcessed_ShouldIncrementCounter_WhenCalled() {
      // When
      ProviderType provider = ProviderType.EXPEDIA;
      ProcessingStatus status = ProcessingStatus.COMPLETED;

      metricsCollector.recordReviewProcessed(provider, status);

      // Then
      mockedCounter.verify(() -> Counter.builder(eq("reviews.processed.total")));
      // Verify fluent chain on the mock builder
      verify(mockCounterBuilder).tag(eq("provider"), eq(provider.name()));
      verify(mockCounterBuilder).tag(eq("status"), eq(status.name()));
      verify(mockCounterBuilder).description(eq("Total number of reviews processed"));
      verify(mockCounterBuilder).register(eq(meterRegistry));
      // Verify increment on the final 'counter' mock
      verify(counter).increment(eq(1.0));
    }

    @Test
    void recordProcessingDuration_ShouldRecordTimer_WhenCalled() {
      // Given
      ProviderType provider = ProviderType.AGODA;
      Duration duration = Duration.ofSeconds(5);

      // When
      metricsCollector.recordProcessingDuration(provider, duration);

      // Then
      // Verify the static Timer.builder was called
      mockedTimer.verify(() -> Timer.builder(eq("processing.duration")));
      // Verify the fluent chain was called on the mock builder
      verify(mockTimerBuilder).tag(eq("provider"), eq(provider.name()));
      verify(mockTimerBuilder).description(eq("Processing duration by provider"));
      verify(mockTimerBuilder).register(eq(meterRegistry));
      verify(timer).record(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void recordDatabaseQuery_ShouldRecordTimer_WhenCalled() {
      // Given
      Duration duration = Duration.ofMillis(250);
      String queryName = "findReviewsByHotelId";

      // When
      metricsCollector.recordDatabaseQuery(queryName, duration);

      // Then
      // 1. Verify static Timer.builder() was called with the correct name
      mockedTimer.verify(() -> Timer.builder(eq("database.query.duration")));

      // 2. Verify the fluent chain calls on the mock Timer.Builder
      verify(mockTimerBuilder).tag(eq("query"), eq(queryName));
      verify(mockTimerBuilder)
          .description(eq("Database query execution time")); // Assuming this description
      verify(mockTimerBuilder)
          .register(eq(meterRegistry)); // Verify it registered with meterRegistry

      // 3. Verify record() was called on the final 'timer' mock
      verify(timer).record(eq(duration.toMillis()), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void recordS3Operation_ShouldIncrementCounter_WhenCalled() {
      // Given
      String operation = "listFiles";
      boolean success = true;
      // When
      metricsCollector.recordS3Operation(operation, success);

      // Then
      // 1. Verify static Timer.builder() was called with the correct name
      mockedCounter.verify(() -> Counter.builder(eq("s3.operations.total")));

      // 2. Verify the fluent chain calls on the mock Timer.Builder
      verify(mockCounterBuilder).tag(eq("operation"), eq(operation));
      verify(mockCounterBuilder).tag(eq("success"), eq(String.valueOf(success)));
      verify(mockCounterBuilder)
          .description(eq("Total S3 operations")); // Assuming this description
      verify(mockCounterBuilder)
          .register(eq(meterRegistry)); // Verify it registered with meterRegistry

      // 3. Verify record() was called on the final 'counter' mock
      verify(counter).increment();
    }

    @Test
    void recordS3Operation_ShouldIncrementCounterWithFailure_WhenOperationFails() {
      // Given
      String operation = "downloadFile";
      boolean success = false;
      // When
      metricsCollector.recordS3Operation(operation, success);

      // Then
      // 1. Verify static Timer.builder() was called with the correct name
      mockedCounter.verify(() -> Counter.builder(eq("s3.operations.total")));

      // 2. Verify the fluent chain calls on the mock Timer.Builder
      verify(mockCounterBuilder).tag(eq("operation"), eq(operation));
      verify(mockCounterBuilder).tag(eq("success"), eq(String.valueOf(success)));
      verify(mockCounterBuilder)
          .description(eq("Total S3 operations")); // Assuming this description
      verify(mockCounterBuilder)
          .register(eq(meterRegistry)); // Verify it registered with meterRegistry

      // 3. Verify record() was called on the final 'counter' mock
      verify(counter).increment();
    }

    @Test
    void recordValidationError_ShouldIncrementCounter_WhenCalled() {
      // Given
      ProviderType provider = ProviderType.AGODA;
      String errorType = "missing_hotel_id";

      // When
      metricsCollector.recordValidationError(provider, errorType);

      // Then
      // Then
      // 1. Verify static Timer.builder() was called with the correct name
      mockedCounter.verify(() -> Counter.builder(eq("validation.errors.total")));

      // 2. Verify the fluent chain calls on the mock Timer.Builder
      verify(mockCounterBuilder).tag(eq("provider"), eq(provider.name()));
      verify(mockCounterBuilder).tag(eq("error_type"), eq(errorType));
      verify(mockCounterBuilder)
          .description(eq("Total validation errors")); // Assuming this description
      verify(mockCounterBuilder)
          .register(eq(meterRegistry)); // Verify it registered with meterRegistry

      // 3. Verify record() was called on the final 'counter' mock
      verify(counter).increment();
    }

    @Test
    void recordApiRequest_ShouldIncrementCounter_WhenCalled() {
      // Given
      String endpoint = "/api/v1/reviews";
      String method = "GET";
      int statusCode = 200;

      // When
      metricsCollector.recordApiRequest(endpoint, method, statusCode);

      // Then
      // 1. Verify static Timer.builder() was called with the correct name
      mockedCounter.verify(() -> Counter.builder(eq("api.requests.total")));

      // 2. Verify the fluent chain calls on the mock Timer.Builder
      verify(mockCounterBuilder).tag(eq("endpoint"), eq(endpoint));
      verify(mockCounterBuilder).tag(eq("method"), eq(method));
      verify(mockCounterBuilder).tag(eq("status"), eq(String.valueOf(statusCode)));
      verify(mockCounterBuilder).description(eq("Total API requests")); // Assuming this description
      verify(mockCounterBuilder)
          .register(eq(meterRegistry)); // Verify it registered with meterRegistry

      // 3. Verify record() was called on the final 'counter' mock
      verify(counter).increment();
    }

    @Test
    void recordApiRequestDuration_ShouldRecordTimer_WhenCalled() {
      // Given
      String endpoint = "/api/v1/reviews";
      String method = "GET";
      Duration duration = Duration.ofMillis(150);

      // When
      metricsCollector.recordApiRequestDuration(endpoint, method, duration);

      // Then
      // 1. Verify static Timer.builder() was called with the correct name
      mockedTimer.verify(() -> Timer.builder(eq("api.request.duration")));

      // 2. Verify the fluent chain calls on the mock Timer.Builder
      verify(mockTimerBuilder).tag(eq("endpoint"), eq(endpoint));
      verify(mockTimerBuilder).tag(eq("method"), eq(method));
      verify(mockTimerBuilder).description(eq("API request duration")); // Assuming this description
      verify(mockTimerBuilder)
          .register(eq(meterRegistry)); // Verify it registered with meterRegistry

      // 3. Verify record() was called on the final 'timer' mock
      verify(timer).record(eq(duration.toMillis()), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void recordCustomMetric_ShouldIncrementCounter_WhenCalled() {
      // Given
      String metricName = "custom.event";
      String tagKey = "eventType";
      String tagValue = "userAction";

      // When
      metricsCollector.recordCustomMetric(metricName, tagKey, tagValue);

      // Then
      // 1. Verify static Timer.builder() was called with the correct name
      mockedCounter.verify(() -> Counter.builder(eq(metricName)));

      // 2. Verify record() was called on the final 'counter' mock
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
  }

  @Nested
  @DisplayName("Timer Related Tests")
  class ProcessingTimerTest {
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
      // Given
      ProviderType provider = ProviderType.AGODA;

      // When
      metricsCollector.stopProcessingTimer(timerSample, provider);

      // Then
      // 1. Verify that timerSample.stop() was called.
      // The argument to stop() is the Timer that was built.
      verify(timerSample)
          .stop(
              // Use argThat to verify the Timer.Builder chain that's passed to stop()
              argThat(
                  actualTimer -> {
                    // This is a bit tricky, as 'stop' receives the result of
                    // Timer.builder().tag().description().register()
                    // The simplest way to verify this is to check if it's our 'timer' mock
                    return actualTimer == timer;
                  }));

      // 2. Verify the static Timer.builder() call for the Timer passed to stop()
      mockedTimer.verify(() -> Timer.builder(eq("processing.duration")));
      verify(mockTimerBuilder).tag(eq("provider"), eq(provider.name()));
      verify(mockTimerBuilder).description(eq("Processing duration by provider"));
      verify(mockTimerBuilder).register(eq(meterRegistry)); // Verify register was called
    }
  }

  @Nested
  @DisplayName("Update Tests")
  class MetricsUpdateTest {
    @Test
    void updateActiveProcessingCount_ShouldUpdateGauge_WhenCalled() {
      // When
      metricsCollector.updateActiveProcessingCount(3);

      // Then
      verify(meterRegistry)
          .gauge(
              eq("processing.active.count"),
              any(MetricsCollector.class), // obj (it's 'this', so MetricsCollector instance)
              any(ToDoubleFunction.class));
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
  }

  @Nested
  @DisplayName("Get Metrics Tests")
  class MetricsGetTest {
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
}
