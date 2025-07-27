package com.reviewsystem.infrastructure.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub implementation for ProcessingMetrics. This is a placeholder for the actual metrics
 * implementation.
 */
@Slf4j
@Component
public class ProcessingMetrics {

  // Scheduled Processing Metrics
  public void recordScheduledProcessingStart() {
    log.info("METRIC: Scheduled processing started");
  }

  public void recordScheduledProcessingSuccess(Long totalProcessed) {
    log.info("METRIC: Scheduled processing completed successfully, processed: {}", totalProcessed);
  }

  public void recordScheduledProcessingFailure(Exception e) {
    log.error("METRIC: Scheduled processing failed", e);
  }

  public void recordScheduledProcessingSkipped() {
    log.info("METRIC: Scheduled processing skipped (lock not acquired)");
  }

  // File Processing Metrics
  public void recordFileProcessingStart(int fileCount) {
    log.info("METRIC: File processing started for {} files", fileCount);
  }

  public void recordFileProcessingComplete(Long totalProcessed) {
    log.info("METRIC: File processing completed, total processed: {}", totalProcessed);
  }

  public void recordFileProcessingError(String fileName, Exception e) {
    log.error("METRIC: File processing error for file: {}", fileName, e);
  }

  // Concurrent Processing Metrics
  public void recordConcurrentProcessingStart(int fileCount) {
    log.info("METRIC: Concurrent processing started for {} files", fileCount);
  }

  public void recordConcurrentProcessingComplete(Long totalProcessed) {
    log.info("METRIC: Concurrent processing completed, total processed: {}", totalProcessed);
  }

  // S3 Metrics
  public void recordS3ListingError(Exception e) {
    log.error("METRIC: S3 listing error", e);
  }

  // Cleanup Metrics
  public void recordCleanupStart() {
    log.info("METRIC: Cleanup process started");
  }

  public void recordCleanupSuccess(Long deletedCount) {
    log.info("METRIC: Cleanup completed successfully, deleted: {}", deletedCount);
  }

  public void recordCleanupFailure(Exception e) {
    log.error("METRIC: Cleanup process failed", e);
  }

  public void recordCleanupSkipped() {
    log.info("METRIC: Cleanup process skipped (lock not acquired)");
  }

  public void recordCleanupComplete(Long deletedCount) {
    log.info("METRIC: Cleanup process completed, deleted: {}", deletedCount);
  }

  public void recordCleanupError(Exception e) {
    log.error("METRIC: Cleanup process error", e);
  }

  // Manual Processing Metrics
  public void recordManualProcessingTrigger() {
    log.info("METRIC: Manual processing triggered");
  }

  // System Metrics
  public void recordGracefulShutdown() {
    log.info("METRIC: Graceful shutdown completed");
  }
}
