package com.reviewsystem.application.service;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.service.FileTrackingService;
import com.reviewsystem.infrastructure.aws.S3Service;
import com.reviewsystem.infrastructure.monitoring.ProcessingMetrics;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Service responsible for orchestrating the processing of review files from S3. Coordinates between
 * S3Service, ReviewProcessingService, and FileTrackingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingOrchestrationService {

  private final S3Service s3Service;
  private final ReviewProcessingService reviewProcessingService;
  private final FileTrackingService fileTrackingService;
  private final ProcessingMetrics processingMetrics;

  @Value("${app.processing.cleanup-retention-days:30}")
  private int cleanupRetentionDays;

  @Value("${app.processing.max-concurrent-files:5}")
  private int maxConcurrentFiles;

  private ExecutorService executorService;

  /**
   * Processes all new files discovered from S3. This is the main orchestration method that
   * coordinates the entire process.
   *
   * @return Total number of records processed across all files
   */
  public Long processNewFiles() {
    log.info("Starting processing of new files from S3");

    try {
      // Discover unprocessed files from S3
      List<S3Object> unprocessedFiles = s3Service.listUnprocessedFiles();

      if (unprocessedFiles.isEmpty()) {
        log.info("No new files found to process");
        processingMetrics.recordFileProcessingStart(0);
        return 0L;
      }

      log.info("Found {} unprocessed files", unprocessedFiles.size());
      processingMetrics.recordFileProcessingStart(unprocessedFiles.size());

      long totalProcessed = 0L;

      // Process each file sequentially (sorted by modification time from S3Service)
      for (S3Object s3Object : unprocessedFiles) {
        String fileKey = s3Object.key();

        try {
          log.debug("Processing file: {}", fileKey);
          Long recordsProcessed = reviewProcessingService.processFile(fileKey);
          totalProcessed += recordsProcessed;
          log.info("Successfully processed file: {} - {} records", fileKey, recordsProcessed);

        } catch (Exception e) {
          log.error("Failed to process file: {} - {}", fileKey, e.getMessage(), e);
          processingMetrics.recordFileProcessingError(fileKey, e);
          // Continue processing other files despite individual failures
        }
      }

      processingMetrics.recordFileProcessingComplete(totalProcessed);
      log.info(
          "Completed processing {} files, total records processed: {}",
          unprocessedFiles.size(),
          totalProcessed);

      return totalProcessed;

    } catch (Exception e) {
      log.error("Error during S3 file listing", e);
      processingMetrics.recordS3ListingError(e);
      throw e;
    }
  }

  /**
   * Processes multiple files concurrently using a thread pool.
   *
   * @param filesToProcess List of file keys to process
   * @return Total number of records processed across all files
   */
  public Long processFilesConcurrently(List<String> filesToProcess) {
    if (filesToProcess.isEmpty()) {
      return 0L;
    }

    log.info("Starting concurrent processing of {} files", filesToProcess.size());
    processingMetrics.recordConcurrentProcessingStart(filesToProcess.size());

    // Initialize executor service if not already done
    if (executorService == null) {
      executorService = Executors.newFixedThreadPool(maxConcurrentFiles);
    }

    List<CompletableFuture<Long>> futures = new ArrayList<>();

    // Submit all files for processing using CompletableFuture.supplyAsync
    for (String fileKey : filesToProcess) {
      CompletableFuture<Long> future =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  log.debug("Concurrently processing file: {}", fileKey);
                  return reviewProcessingService.processFile(fileKey);
                } catch (Exception e) {
                  log.error(
                      "Failed to process file concurrently: {} - {}", fileKey, e.getMessage(), e);
                  processingMetrics.recordFileProcessingError(fileKey, e);
                  return 0L; // Return 0 for failed files
                }
              },
              executorService);

      futures.add(future);
    }

    // Wait for all futures to complete and sum results
    long totalProcessed = 0L;
    for (CompletableFuture<Long> future : futures) {
      try {
        totalProcessed += future.get(); // Use get() instead of join() for better exception handling
      } catch (InterruptedException e) {
        log.error("Processing interrupted for future", e);
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        log.error("Execution exception during concurrent processing", e.getCause());
        // Continue processing other futures
      }
    }

    processingMetrics.recordConcurrentProcessingComplete(totalProcessed);
    log.info("Completed concurrent processing, total records processed: {}", totalProcessed);

    return totalProcessed;
  }

  /**
   * Cleans up old processed file records from the database. Removes records older than the
   * configured retention period.
   *
   * @return Number of records deleted
   */
  public Long cleanupOldProcessedFiles() {
    log.info("Starting cleanup of old processed file records");
    processingMetrics.recordCleanupStart();

    try {
      LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanupRetentionDays);
      Long deletedCount = fileTrackingService.deleteProcessedFilesBefore(cutoffDate);

      processingMetrics.recordCleanupComplete(deletedCount);
      log.info("Cleanup completed, deleted {} old file records", deletedCount);

      return deletedCount;

    } catch (Exception e) {
      log.error("Error during cleanup of old processed files", e);
      processingMetrics.recordCleanupError(e);
      throw e;
    }
  }

  /**
   * Gets the current processing status.
   *
   * @return Current processing status
   */
  public ProcessingStatus getProcessingStatus() {
    // For now, return IDLE. In a real implementation, this would check
    // if any processing is currently in progress
    return ProcessingStatus.IDLE;
  }

  /** Gracefully shuts down the executor service. */
  @PreDestroy
  public void shutdown() {
    if (executorService != null && !executorService.isShutdown()) {
      log.info("Shutting down processing orchestration executor service");
      executorService.shutdown();

      try {
        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
          log.warn("Executor service did not terminate gracefully, forcing shutdown");
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        log.warn("Interrupted while waiting for executor service shutdown");
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }

      processingMetrics.recordGracefulShutdown();
    }
  }

  /**
   * Triggers processing of files discovered since a specific date.
   *
   * @param sinceDate Date to filter files from
   * @return Total number of records processed
   */
  public Long processFilesSince(LocalDateTime sinceDate) {
    log.info("Processing files since: {}", sinceDate);

    try {
      List<S3Object> newFiles = s3Service.listNewFiles(java.util.Optional.of(sinceDate));

      if (newFiles.isEmpty()) {
        log.info("No files found since: {}", sinceDate);
        return 0L;
      }

      // Extract file keys and process
      List<String> fileKeys = newFiles.stream().map(S3Object::key).collect(Collectors.toList());

      return processFilesConcurrently(fileKeys);

    } catch (Exception e) {
      log.error("Error processing files since: {}", sinceDate, e);
      throw e;
    }
  }

  /**
   * Gets processing statistics for monitoring and reporting.
   *
   * @return Processing statistics
   */
  public ProcessingStats getProcessingStats() {
    return ProcessingStats.builder()
        .totalProcessedFiles(fileTrackingService.getTotalProcessedFiles())
        .processedFilesToday(fileTrackingService.getProcessedFilesToday())
        .lastProcessingTime(getLastProcessingTime())
        .build();
  }

  private LocalDateTime getLastProcessingTime() {
    // This would typically be retrieved from the database or cache
    // For now, return null indicating no previous processing time available
    return null;
  }

  /** Value object for processing statistics */
  @lombok.Value
  @lombok.Builder
  public static class ProcessingStats {
    Long totalProcessedFiles;
    Long processedFilesToday;
    LocalDateTime lastProcessingTime;
  }
}
