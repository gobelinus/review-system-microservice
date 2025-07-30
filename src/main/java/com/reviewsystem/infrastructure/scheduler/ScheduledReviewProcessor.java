package com.reviewsystem.infrastructure.scheduler;

import com.reviewsystem.application.service.ProcessingOrchestrationService;
import com.reviewsystem.infrastructure.monitoring.ProcessingMetrics;
import java.util.concurrent.locks.Lock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled processor for handling periodic review file processing. Implements distributed locking
 * to prevent concurrent execution across multiple instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledReviewProcessor {

  private static final String PROCESSING_LOCK_KEY = "review-processing-lock";
  private static final String CLEANUP_LOCK_KEY = "cleanup-processing-lock";

  private final ProcessingOrchestrationService orchestrationService;
  private final LockRegistry lockRegistry;
  private final ProcessingMetrics processingMetrics;

  /**
   * Scheduled method to process new review files. Runs every 30 minutes and uses distributed
   * locking to prevent concurrent execution.
   */
  // ToDo: 10 seconds, on production should be increased
  @Scheduled(fixedDelayString = "${app.scheduling.review-processing.interval:10000}")
  public void processReviews() {
    Lock lock = lockRegistry.obtain(PROCESSING_LOCK_KEY);
    boolean lockAcquired = false;

    try {
      log.info("Attempting to acquire processing lock for scheduled review processing");
      lockAcquired = lock.tryLock();

      if (!lockAcquired) {
        log.warn(
            "Could not acquire processing lock - another instance is already processing reviews");
        processingMetrics.recordScheduledProcessingSkipped();
        return;
      }

      log.info("Processing lock acquired - starting scheduled review processing");
      processingMetrics.recordScheduledProcessingStart();

      Long totalProcessed = orchestrationService.processNewFiles();

      log.info(
          "Scheduled review processing completed successfully. Total reviews processed: {}",
          totalProcessed);
      processingMetrics.recordScheduledProcessingSuccess(totalProcessed);

    } catch (Exception e) {
      log.error("Error occurred during scheduled review processing", e);
      processingMetrics.recordScheduledProcessingFailure(e);
      // Don't rethrow - we want the scheduler to continue running
    } finally {
      if (lockAcquired) {
        try {
          lock.unlock();
          log.debug("Processing lock released");
        } catch (Exception e) {
          log.error("Error releasing processing lock", e);
        }
      }
    }
  }

  /**
   * Scheduled method to cleanup old processed file records. Runs daily at 2 AM to remove old file
   * tracking records.
   */
  @Scheduled(cron = "${app.scheduling.cleanup.cron:0 0 2 * * ?}") // Daily at 2 AM default
  public void cleanupOldProcessedFiles() {
    Lock lock = lockRegistry.obtain(CLEANUP_LOCK_KEY);
    boolean lockAcquired = false;

    try {
      log.info("Attempting to acquire cleanup lock for scheduled file cleanup");
      lockAcquired = lock.tryLock();

      if (!lockAcquired) {
        log.warn("Could not acquire cleanup lock - another instance is already performing cleanup");
        processingMetrics.recordCleanupSkipped();
        return;
      }

      log.info("Cleanup lock acquired - starting scheduled file cleanup");

      Long deletedCount = orchestrationService.cleanupOldProcessedFiles();

      log.info(
          "Scheduled file cleanup completed successfully. Total records deleted: {}", deletedCount);
      processingMetrics.recordCleanupSuccess(deletedCount);

    } catch (Exception e) {
      log.error("Error occurred during scheduled file cleanup", e);
      processingMetrics.recordCleanupFailure(e);
      // Don't rethrow - we want the scheduler to continue running
    } finally {
      if (lockAcquired) {
        try {
          lock.unlock();
          log.debug("Cleanup lock released");
        } catch (Exception e) {
          log.error("Error releasing cleanup lock", e);
        }
      }
    }
  }

  /**
   * Health check method to verify scheduler is functioning. Can be called by health checks to
   * ensure scheduling is active.
   */
  public boolean isSchedulerHealthy() {
    try {
      // Try to acquire and immediately release the lock to test lock registry
      Lock testLock = lockRegistry.obtain("health-check-lock");
      boolean acquired = testLock.tryLock();
      if (acquired) {
        testLock.unlock();
        return true;
      }
      return false;
    } catch (Exception e) {
      log.error("Scheduler health check failed", e);
      return false;
    }
  }
}
