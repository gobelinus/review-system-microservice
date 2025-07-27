package com.reviewsystem.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.reviewsystem.application.service.ProcessingOrchestrationService;
import com.reviewsystem.infrastructure.monitoring.ProcessingMetrics;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test-postgres")
class ScheduledReviewProcessorTest {

  @Mock private ProcessingOrchestrationService orchestrationService;

  @Mock private LockRegistry lockRegistry;

  @Mock private ProcessingMetrics processingMetrics;

  @Mock private Lock lock;

  @InjectMocks private ScheduledReviewProcessor scheduledReviewProcessor;

  @BeforeEach
  void setUp() {
    when(lockRegistry.obtain(anyString())).thenReturn(lock);
  }

  @Test
  void processReviews_ShouldAcquireLockAndProcessSuccessfully() {
    // Given
    when(lock.tryLock()).thenReturn(true);
    when(orchestrationService.processNewFiles()).thenReturn(5L);

    // When
    scheduledReviewProcessor.processReviews();

    // Then
    verify(lock).tryLock();
    verify(orchestrationService).processNewFiles();
    verify(processingMetrics).recordScheduledProcessingSuccess(5L);
    verify(lock).unlock();
  }

  @Test
  void processReviews_ShouldSkipProcessingWhenLockNotAcquired() {
    // Given
    when(lock.tryLock()).thenReturn(false);

    // When
    scheduledReviewProcessor.processReviews();

    // Then
    verify(lock).tryLock();
    verify(orchestrationService, never()).processNewFiles();
    verify(processingMetrics).recordScheduledProcessingSkipped();
    verify(lock, never()).unlock();
  }

  @Test
  void processReviews_ShouldHandleProcessingException() {
    // Given
    when(lock.tryLock()).thenReturn(true);
    RuntimeException exception = new RuntimeException("Processing failed");
    when(orchestrationService.processNewFiles()).thenThrow(exception);

    // When
    scheduledReviewProcessor.processReviews();

    // Then
    verify(lock).tryLock();
    verify(orchestrationService).processNewFiles();
    verify(processingMetrics).recordScheduledProcessingFailure(exception);
    verify(lock).unlock();
  }

  @Test
  void processReviews_ShouldAlwaysReleaseLockInFinallyBlock() {
    // Given
    when(lock.tryLock()).thenReturn(true);
    RuntimeException exception = new RuntimeException("Processing failed");
    when(orchestrationService.processNewFiles()).thenThrow(exception);

    // When
    scheduledReviewProcessor.processReviews();

    // Then
    verify(lock).unlock();
  }

  @Test
  void processReviews_ShouldNotUnlockWhenLockNotAcquired() {
    // Given
    when(lock.tryLock()).thenReturn(false);

    // When
    scheduledReviewProcessor.processReviews();

    // Then
    verify(lock, never()).unlock();
  }

  @Test
  void processReviews_ShouldLogProcessingStart() {
    // Given
    when(lock.tryLock()).thenReturn(true);
    when(orchestrationService.processNewFiles()).thenReturn(3L);

    // When
    scheduledReviewProcessor.processReviews();

    // Then
    verify(processingMetrics).recordScheduledProcessingStart();
    verify(processingMetrics).recordScheduledProcessingSuccess(3L);
  }

  @Test
  void processReviews_ShouldHandleLockAcquisitionTimeout() {
    // Given
    when(lock.tryLock()).thenReturn(false);

    // When
    scheduledReviewProcessor.processReviews();

    // Then
    verify(processingMetrics).recordScheduledProcessingSkipped();
  }

  @Test
  void cleanupOldProcessedFiles_ShouldAcquireLockAndCleanSuccessfully() {
    // Given
    when(lock.tryLock()).thenReturn(true);
    when(orchestrationService.cleanupOldProcessedFiles()).thenReturn(10L);

    // When
    scheduledReviewProcessor.cleanupOldProcessedFiles();

    // Then
    verify(lock).tryLock();
    verify(orchestrationService).cleanupOldProcessedFiles();
    verify(processingMetrics).recordCleanupSuccess(10L);
    verify(lock).unlock();
  }

  @Test
  void cleanupOldProcessedFiles_ShouldSkipWhenLockNotAcquired() {
    // Given
    when(lock.tryLock()).thenReturn(false);

    // When
    scheduledReviewProcessor.cleanupOldProcessedFiles();

    // Then
    verify(lock).tryLock();
    verify(orchestrationService, never()).cleanupOldProcessedFiles();
    verify(processingMetrics).recordCleanupSkipped();
    verify(lock, never()).unlock();
  }

  @Test
  void cleanupOldProcessedFiles_ShouldHandleCleanupException() {
    // Given
    when(lock.tryLock()).thenReturn(true);
    RuntimeException exception = new RuntimeException("Cleanup failed");
    when(orchestrationService.cleanupOldProcessedFiles()).thenThrow(exception);

    // When
    scheduledReviewProcessor.cleanupOldProcessedFiles();

    // Then
    verify(lock).tryLock();
    verify(orchestrationService).cleanupOldProcessedFiles();
    verify(processingMetrics).recordCleanupFailure(exception);
    verify(lock).unlock();
  }
}
