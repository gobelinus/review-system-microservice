package com.reviewsystem.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.reviewsystem.application.service.ProcessingOrchestrationService;
import com.reviewsystem.infrastructure.monitoring.ProcessingMetrics;
import jakarta.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test-postgres")
class SchedulingIntegrationTest {

  @MockBean private ProcessingOrchestrationService orchestrationService;

  @MockBean private ProcessingMetrics processingMetrics;

  @Resource private LockRegistry lockRegistry;

  @Resource private ScheduledReviewProcessor scheduledReviewProcessor;

  @Test
  void scheduledProcessing_ShouldPreventConcurrentExecution() throws InterruptedException {
    // Given
    CountDownLatch latch = new CountDownLatch(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    // Mock a long-running process
    when(orchestrationService.processNewFiles())
        .thenAnswer(
            invocation -> {
              Thread.sleep(1000); // Simulate processing time
              return 10L;
            });

    // When - Execute two instances simultaneously
    executor.submit(
        () -> {
          scheduledReviewProcessor.processReviews();
          latch.countDown();
        });

    executor.submit(
        () -> {
          scheduledReviewProcessor.processReviews();
          latch.countDown();
        });

    // Wait for both to complete
    assertTrue(latch.await(5, TimeUnit.SECONDS));

    // Then - Only one should have executed the actual processing
    verify(orchestrationService, times(1)).processNewFiles();
    verify(processingMetrics, times(1)).recordScheduledProcessingSuccess(10L);
    verify(processingMetrics, times(1)).recordScheduledProcessingSkipped();

    executor.shutdown();
  }

  @Test
  void scheduledCleanup_ShouldPreventConcurrentExecution() throws InterruptedException {
    // Given
    CountDownLatch latch = new CountDownLatch(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    // Mock a long-running cleanup process
    when(orchestrationService.cleanupOldProcessedFiles())
        .thenAnswer(
            invocation -> {
              Thread.sleep(1000); // Simulate cleanup time
              return 5L;
            });

    // When - Execute two cleanup instances simultaneously
    executor.submit(
        () -> {
          scheduledReviewProcessor.cleanupOldProcessedFiles();
          latch.countDown();
        });

    executor.submit(
        () -> {
          scheduledReviewProcessor.cleanupOldProcessedFiles();
          latch.countDown();
        });

    // Wait for both to complete
    assertTrue(latch.await(5, TimeUnit.SECONDS));

    // Then - Only one should have executed the actual cleanup
    verify(orchestrationService, times(1)).cleanupOldProcessedFiles();
    verify(processingMetrics, times(1)).recordCleanupSuccess(5L);
    verify(processingMetrics, times(1)).recordCleanupSkipped();

    executor.shutdown();
  }

  @Test
  void lockRegistry_ShouldProvideDistributedLocking() {
    // Given
    String lockKey = "integration-test-lock";

    // When
    Lock lock1 = lockRegistry.obtain(lockKey);
    Lock lock2 = lockRegistry.obtain(lockKey);

    // Then
    assertNotNull(lock1);
    assertNotNull(lock2);
    assertSame(lock1, lock2); // Should be the same lock instance
  }

  @Test
  void schedulerHealthCheck_ShouldReturnTrueWhenHealthy() {
    // When
    boolean isHealthy = scheduledReviewProcessor.isSchedulerHealthy();

    // Then
    assertTrue(isHealthy);
  }

  @Test
  void schedulerHealthCheck_ShouldHandleLockRegistryFailure() {
    // Given - Create a new processor with a failing lock registry
    LockRegistry failingLockRegistry = mock(LockRegistry.class);
    when(failingLockRegistry.obtain(anyString()))
        .thenThrow(new RuntimeException("Lock registry failed"));

    ScheduledReviewProcessor processorWithFailingRegistry =
        new ScheduledReviewProcessor(orchestrationService, failingLockRegistry, processingMetrics);

    // When
    boolean isHealthy = processorWithFailingRegistry.isSchedulerHealthy();

    // Then
    assertFalse(isHealthy);
  }

  @Test
  void processReviews_ShouldHandleInterruptedException() throws InterruptedException {
    // Given
    when(orchestrationService.processNewFiles())
        .thenAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              throw new InterruptedException("Processing interrupted");
            });

    // When
    scheduledReviewProcessor.processReviews();

    // Then
    verify(processingMetrics).recordScheduledProcessingFailure(any(InterruptedException.class));
    assertTrue(Thread.currentThread().isInterrupted());

    // Clear the interrupt flag for subsequent tests
    Thread.interrupted();
  }
}
