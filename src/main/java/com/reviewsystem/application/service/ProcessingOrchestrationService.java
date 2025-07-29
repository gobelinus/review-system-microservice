package com.reviewsystem.application.service;

import com.reviewsystem.application.dto.request.ProcessingTriggerRequest;
import com.reviewsystem.application.dto.response.ProcessingStatusResponse;
import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.ProcessedFile;
import com.reviewsystem.domain.entity.ProcessingJob;
import com.reviewsystem.domain.repository.ProcessedFileRepository;
import com.reviewsystem.domain.repository.ProcessingJobRepository;
import com.reviewsystem.domain.service.FileTrackingService;
import com.reviewsystem.infrastructure.aws.S3Service;
import com.reviewsystem.infrastructure.monitoring.ProcessingMetrics;
import com.reviewsystem.repository.ReviewRepository;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
  private final ProcessedFileRepository processedFileRepository;
  private final ReviewRepository reviewRepository;
  private final ProcessingJobRepository processingJobRepository;
  private final CacheManager cacheManager;

  @Value("${app.processing.cleanup-retention-days:30}")
  private int cleanupRetentionDays;

  @Value("${app.processing.max-concurrent-files:5}")
  private int maxConcurrentFiles;

  @Value("${processing.trigger.max-files-per-request:50}")
  private int maxFilesPerRequest;

  // Configuration for health thresholds
  @Value("${processing.health.failure-rate-threshold:0.1}") // 10% failure rate threshold
  private double failureRateThreshold;

  @Value("${processing.health.staleness-threshold-hours:24}") // 24 hours without processing
  private int stalenessThresholdHours;

  // Metrics tracking
  private final AtomicLong totalProcessedFiles = new AtomicLong(0);
  private final AtomicLong totalFailedFiles = new AtomicLong(0);
  private final AtomicLong totalProcessedReviews = new AtomicLong(0);
  private final AtomicInteger currentlyProcessingFiles = new AtomicInteger(0);

  // Track if processing is currently running to prevent concurrent executions
  private final AtomicBoolean isProcessingActive = new AtomicBoolean(false);
  private final AtomicBoolean isScheduledProcessingPaused = new AtomicBoolean(false);

  // Store active processing jobs
  private final Map<String, CompletableFuture<String>> activeJobs = new ConcurrentHashMap<>();

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
   * Returns comprehensive health status of the processing system including: - Overall health status
   * (UP/DOWN/DEGRADED) - Failure rate monitoring - Processing statistics - Last processing activity
   * - Current processing load - Error details if applicable
   *
   * @return Map containing health status information
   */
  public Map<String, Object> getProcessingHealthStatus() {
    Map<String, Object> healthStatus = new HashMap<>();

    try {
      // Get processing statistics from last 24 hours
      LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);

      // Database queries for recent activity
      long recentProcessedFiles =
          processedFileRepository.countByProcessingCompletedAtBefore(last24Hours);
      long recentFailedFiles =
          processedFileRepository.countByStatusAndProcessingCompletedAtBefore(
              ProcessingStatus.FAILED, last24Hours);
      long recentProcessedReviews = reviewRepository.countByCreatedAtBefore(last24Hours);

      // Get last processing activity
      Optional<ProcessedFile> lastProcessedFile =
          processedFileRepository.findTopByOrderByProcessingCompletedAtDesc();

      // Calculate failure rate
      double currentFailureRate =
          recentProcessedFiles > 0 ? (double) recentFailedFiles / recentProcessedFiles : 0.0;

      // Determine overall health status
      String status = determineHealthStatus(currentFailureRate, lastProcessedFile);

      // Build health status response
      healthStatus.put("status", status);
      healthStatus.put("timestamp", LocalDateTime.now());

      // Processing statistics
      Map<String, Object> statistics = new HashMap<>();
      statistics.put("totalProcessedFiles", totalProcessedFiles.get());
      statistics.put("totalFailedFiles", totalFailedFiles.get());
      statistics.put("totalProcessedReviews", totalProcessedReviews.get());
      statistics.put("recentProcessedFiles24h", recentProcessedFiles);
      statistics.put("recentFailedFiles24h", recentFailedFiles);
      statistics.put("recentProcessedReviews24h", recentProcessedReviews);
      statistics.put("currentlyProcessingFiles", currentlyProcessingFiles.get());
      healthStatus.put("statistics", statistics);

      // Failure rate monitoring
      Map<String, Object> failureRateInfo = new HashMap<>();
      failureRateInfo.put(
          "currentFailureRate",
          Math.round(currentFailureRate * 10000.0) / 100.0); // Percentage with 2 decimals
      failureRateInfo.put(
          "failureRateThreshold", Math.round(failureRateThreshold * 10000.0) / 100.0);
      failureRateInfo.put("isWithinThreshold", currentFailureRate <= failureRateThreshold);
      healthStatus.put("failureRate", failureRateInfo);

      // Last processing activity
      Map<String, Object> lastActivity = new HashMap<>();
      if (lastProcessedFile.isPresent()) {
        ProcessedFile lastFile = lastProcessedFile.get();
        lastActivity.put("lastProcessedFile", lastFile.getS3Key());
        lastActivity.put("lastProcessingCompletedAt", lastFile.getProcessingCompletedAt());
        lastActivity.put("lastProcessingStatus", lastFile.getProcessingStatus());

        long hoursSinceLastProcessing =
            ChronoUnit.HOURS.between(lastFile.getProcessingCompletedAt(), LocalDateTime.now());
        lastActivity.put("hoursSinceLastProcessing", hoursSinceLastProcessing);
        lastActivity.put("isStale", hoursSinceLastProcessing > stalenessThresholdHours);
      } else {
        lastActivity.put("lastProcessedFile", null);
        lastActivity.put("lastProcessedAt", null);
        lastActivity.put("isStale", true);
      }
      healthStatus.put("lastActivity", lastActivity);

      // Current processing load
      Map<String, Object> processingLoad = new HashMap<>();
      processingLoad.put("currentlyProcessing", currentlyProcessingFiles.get());
      processingLoad.put("maxConcurrentFiles", maxConcurrentFiles);
      processingLoad.put(
          "loadPercentage",
          Math.round(((double) currentlyProcessingFiles.get() / maxConcurrentFiles) * 10000.0)
              / 100.0);
      processingLoad.put("isOverloaded", currentlyProcessingFiles.get() >= maxConcurrentFiles);
      healthStatus.put("processingLoad", processingLoad);

      // Add detailed errors if status is not UP
      if (!"UP".equals(status)) {
        healthStatus.put("issues", getHealthIssues(currentFailureRate, lastProcessedFile));
      }

    } catch (Exception e) {
      // If health check itself fails, return DOWN status
      healthStatus.put("status", "DOWN");
      healthStatus.put("timestamp", LocalDateTime.now());
      healthStatus.put("error", "Health check failed: " + e.getMessage());
      healthStatus.put("exception", e.getClass().getSimpleName());
    }

    return healthStatus;
  }

  /** Determines the overall health status based on various metrics */
  private String determineHealthStatus(
      double failureRate, Optional<ProcessedFile> lastProcessedFile) {
    List<String> issues = new ArrayList<>();

    // Check failure rate
    if (failureRate > failureRateThreshold) {
      issues.add("High failure rate: " + Math.round(failureRate * 10000.0) / 100.0 + "%");
    }

    // Check staleness
    if (lastProcessedFile.isPresent()) {
      long hoursSinceLastProcessing =
          ChronoUnit.HOURS.between(
              lastProcessedFile.get().getProcessingCompletedAt(), LocalDateTime.now());
      if (hoursSinceLastProcessing > stalenessThresholdHours) {
        issues.add("No processing activity for " + hoursSinceLastProcessing + " hours");
      }
    } else {
      issues.add("No processing history found");
    }

    // Check processing load
    if (currentlyProcessingFiles.get() >= maxConcurrentFiles) {
      issues.add("Processing system overloaded");
    }

    // Determine status based on issues
    if (issues.isEmpty()) {
      return "UP";
    } else if (issues.size() == 1 && failureRate <= failureRateThreshold * 2) {
      return "DEGRADED"; // Minor issues
    } else {
      return "DOWN"; // Major issues
    }
  }

  /** Gets detailed list of health issues */
  private List<String> getHealthIssues(
      double failureRate, Optional<ProcessedFile> lastProcessedFile) {
    List<String> issues = new ArrayList<>();

    if (failureRate > failureRateThreshold) {
      issues.add(
          "Failure rate ("
              + Math.round(failureRate * 10000.0) / 100.0
              + "%) exceeds threshold ("
              + Math.round(failureRateThreshold * 10000.0) / 100.0
              + "%)");
    }

    if (lastProcessedFile.isPresent()) {
      long hoursSinceLastProcessing =
          ChronoUnit.HOURS.between(
              lastProcessedFile.get().getProcessingCompletedAt(), LocalDateTime.now());
      if (hoursSinceLastProcessing > stalenessThresholdHours) {
        issues.add(
            "Processing system is stale - last activity "
                + hoursSinceLastProcessing
                + " hours ago");
      }
    } else {
      issues.add("No processing history available");
    }

    if (currentlyProcessingFiles.get() >= maxConcurrentFiles) {
      issues.add(
          "Processing capacity exceeded: "
              + currentlyProcessingFiles.get()
              + "/"
              + maxConcurrentFiles
              + " concurrent files");
    }

    return issues;
  }

  // Helper methods to update metrics (called by other processing methods)
  public void incrementProcessedFiles() {
    totalProcessedFiles.incrementAndGet();
  }

  public void incrementFailedFiles() {
    totalFailedFiles.incrementAndGet();
  }

  public void addProcessedReviews(long count) {
    totalProcessedReviews.addAndGet(count);
  }

  public void incrementCurrentlyProcessing() {
    currentlyProcessingFiles.incrementAndGet();
  }

  public void decrementCurrentlyProcessing() {
    currentlyProcessingFiles.decrementAndGet();
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

  /**
   * Triggers manual processing of review files from S3 bucket. This method provides immediate
   * processing capability for administrators and returns a processing job identifier for tracking
   * progress.
   *
   * @param request Processing trigger request containing processing parameters
   * @return Processing job ID or status message indicating the result
   */
  public String triggerProcessing(ProcessingTriggerRequest request) {
    log.info("Processing trigger requested: {}", request);

    try {
      // Validate request parameters
      validateProcessingRequest(request);

      // Check if processing is already active
      if (!isProcessingActive.compareAndSet(false, true)) {
        String message =
            "Processing is already in progress. Please wait for current processing to complete.";
        log.warn(message);
        return message;
      }

      // Generate unique processing job ID
      String processingJobId = generateProcessingJobId();

      // Determine processing mode
      if (request.isAsynchronous()) {
        // Asynchronous processing - start in background and return immediately
        CompletableFuture.supplyAsync(() -> executeProcessing(request, processingJobId))
            .whenComplete(
                (result, throwable) -> {
                  isProcessingActive.set(false);
                  if (throwable != null) {
                    log.error(
                        "Asynchronous processing failed for job: {}", processingJobId, throwable);
                  } else {
                    log.info(
                        "Asynchronous processing completed for job: {} with result: {}",
                        processingJobId,
                        result);
                  }
                });

        log.info("Asynchronous processing started with job ID: {}", processingJobId);
        return "Processing started asynchronously. Job ID: " + processingJobId;

      } else {
        // Synchronous processing - execute and wait for completion
        try {
          String result = executeProcessing(request, processingJobId);
          log.info(
              "Synchronous processing completed for job: {} with result: {}",
              processingJobId,
              result);
          return result;
        } finally {
          isProcessingActive.set(false);
        }
      }

    } catch (IllegalArgumentException e) {
      log.warn("Invalid processing request: {}", e.getMessage());
      return "Invalid request: " + e.getMessage();

    } catch (Exception e) {
      log.error("Failed to trigger processing", e);
      isProcessingActive.set(false);
      return "Processing trigger failed: " + e.getMessage();
    }
  }

  /** Validates the processing trigger request */
  private void validateProcessingRequest(ProcessingTriggerRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Processing request cannot be null");
    }

    if (request.getMaxFiles() != null && request.getMaxFiles() <= 0) {
      throw new IllegalArgumentException("Max files must be greater than 0");
    }

    if (request.getMaxFiles() != null && request.getMaxFiles() > maxFilesPerRequest) {
      throw new IllegalArgumentException(
          "Max files cannot exceed " + maxFilesPerRequest + " per request");
    }

    if (request.getS3Prefix() != null && request.getS3Prefix().trim().isEmpty()) {
      throw new IllegalArgumentException("S3 prefix cannot be empty if specified");
    }

    if (request.getProvider() != null) {
      throw new IllegalArgumentException("Provider filter cannot be empty if specified");
    }
  }

  /** Executes the actual processing logic */
  private String executeProcessing(ProcessingTriggerRequest request, String jobId) {
    try {
      log.info("Starting processing execution for job: {}", jobId);

      // Step 1: Discover files to process
      List<S3Object> newFilesToProcess = s3Service.listNewFiles();

      if (newFilesToProcess.isEmpty()) {
        String message = "No new files found to process";
        log.info("{} for job: {}", message, jobId);
        return message + ". Job ID: " + jobId;
      }

      log.info("Found {} files to process for job: {}", newFilesToProcess.size(), jobId);

      // Step 2: Filter files if provider filter is specified
      if (request.getProvider() != null) {
        newFilesToProcess =
            filterS3ObjectByProvider(newFilesToProcess, request.getProvider().getDisplayName());
        log.info(
            "After provider filtering: {} files remaining for job: {}",
            newFilesToProcess.size(),
            jobId);
      }

      // Step 3: Process files
      int processedCount = 0;
      int failedCount = 0;
      int totalReviews = 0;

      for (S3Object newFile : newFilesToProcess) {
        String fileName = newFile.key();
        try {
          log.debug("Processing file: {} for job: {}", fileName, jobId);

          // Check if file was already processed (idempotent check)
          if (fileTrackingService.isFileAlreadyProcessed(fileName)) {
            log.debug("File {} already processed, skipping", fileName);
            continue;
          }

          // Process the file
          int reviewsProcessed = reviewProcessingService.processFile(fileName).intValue();
          totalReviews += reviewsProcessed;
          processedCount++;

          // Mark file as processed
          fileTrackingService.markProcessingCompleted(fileName, reviewsProcessed, 0);

          log.debug("Successfully processed file: {} with {} reviews", fileName, reviewsProcessed);

        } catch (Exception e) {
          failedCount++;
          log.error("Failed to process file: {} for job: {}", fileName, jobId, e);

          // Mark file as failed
          try {
            fileTrackingService.markProcessingFailed(fileName, e.getMessage());
          } catch (Exception trackingException) {
            log.error("Failed to mark file as failed: {}", fileName, trackingException);
          }
        }
      }

      // Step 4: Generate processing summary
      String summary =
          String.format(
              "Processing completed for job: %s. Files processed: %d, Failed: %d, Total reviews: %d",
              jobId, processedCount, failedCount, totalReviews);

      log.info(summary);

      // Update metrics
      updateProcessingMetrics(processedCount, failedCount, totalReviews);

      return summary;

    } catch (Exception e) {
      String errorMessage =
          "Processing execution failed for job: " + jobId + ". Error: " + e.getMessage();
      log.error(errorMessage, e);
      return errorMessage;
    }
  }

  /** Filters files by provider if specified */
  private List<S3Object> filterS3ObjectByProvider(List<S3Object> files, String providerFilter) {
    return files.stream()
        .filter(s3File -> s3File.key().toLowerCase().contains(providerFilter.toLowerCase()))
        .collect(Collectors.toList());
  }

  /** Generates a unique processing job ID */
  private String generateProcessingJobId() {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    String randomSuffix = String.valueOf(System.nanoTime() % 10000);
    return "PROC-" + timestamp + "-" + randomSuffix;
  }

  /** Updates processing metrics after completion */
  private void updateProcessingMetrics(int processedFiles, int failedFiles, int totalReviews) {
    // Update atomic counters (from previous health status implementation)
    totalProcessedFiles.addAndGet(processedFiles);
    totalFailedFiles.addAndGet(failedFiles);
    totalProcessedReviews.addAndGet(totalReviews);

    log.debug(
        "Updated processing metrics - Processed: {}, Failed: {}, Reviews: {}",
        processedFiles,
        failedFiles,
        totalReviews);
  }

  /** Checks if processing is currently active */
  public boolean isProcessingActive() {
    return isProcessingActive.get();
  }

  public ProcessingStatusResponse getProcessingStatus(String processingId) {
    Optional<ProcessingJob> jobOpt = processingJobRepository.findById(processingId);

    if (jobOpt.isEmpty()) {
      throw new IllegalArgumentException("Processing job not found: " + processingId);
    }

    ProcessingJob job = jobOpt.get();
    return convertToStatusResponse(job);
  }

  private ProcessingStatusResponse convertToStatusResponse(ProcessingJob job) {
    return ProcessingStatusResponse.builder()
        .id(job.getProcessingId())
        .status(job.getStatus())
        .provider(job.getProvider())
        .startTime(job.getStartTime())
        .endTime(job.getEndTime())
        .duration(job.getDuration())
        .totalFiles(job.getTotalFiles())
        .processedFiles(job.getProcessedFiles())
        .failedReviews(job.getFailedFiles())
        .totalReviews(job.getTotalReviews())
        .progressPercent(
            job.getTotalFiles() != null && job.getTotalFiles() > 0
                ? ((job.getProcessedFiles() != null ? job.getProcessedFiles() : 0)
                        + (job.getFailedFiles() != null ? job.getFailedFiles() : 0))
                    * 100.0
                    / job.getTotalFiles()
                : 0.0)
        .errorMessage(job.getErrorMessage())
        .processedFileNames(job.getProcessedFileNames())
        .failedFileNames(job.getFailedFileNames())
        .triggeredBy(job.getTriggeredBy())
        .isAsynchronous(job.getIsAsynchronous())
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .build();
  }

  public List<ProcessingStatusResponse> getAllProcessingStatuses() {
    List<ProcessingJob> jobs = processingJobRepository.findAll();
    return jobs.stream().map(this::convertToStatusResponse).collect(Collectors.toList());
  }

  @Transactional
  public void stopProcessing(String processingId) {
    log.info("Stopping processing job: {}", processingId);

    Optional<ProcessingJob> jobOpt = processingJobRepository.findById(processingId);
    if (jobOpt.isEmpty()) {
      throw new IllegalArgumentException("Processing job not found: " + processingId);
    }

    ProcessingJob job = jobOpt.get();

    // Check if job is stoppable
    if (job.getStatus() != ProcessingStatus.IN_PROGRESS
        && job.getStatus() != ProcessingStatus.PENDING) {
      throw new IllegalStateException("Cannot stop job in status: " + job.getStatus());
    }

    // Cancel the future if it exists
    CompletableFuture<String> future = activeJobs.get(processingId);
    if (future != null) {
      future.cancel(true);
      activeJobs.remove(processingId);
    }

    // Update job status
    job.markAsCancelled();
    processingJobRepository.save(job);

    log.info("Processing job stopped: {}", processingId);
  }

  public List<ProcessingStatusResponse> getProcessingHistory(
      ProviderType provider, LocalDate startDate, LocalDate endDate) {
    return getProcessingHistory(provider, startDate, endDate, null);
  }

  public List<ProcessingStatusResponse> getProcessingHistory(
      ProviderType provider, LocalDate startDate, LocalDate endDate, Integer limit) {
    LocalDateTime startDateTime =
        startDate != null ? startDate.atStartOfDay() : LocalDateTime.now().minusDays(30);
    LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : LocalDateTime.now();

    List<ProcessingJob> jobs;

    if (limit != null && limit > 0) {
      jobs =
          processingJobRepository
              .findByProviderAndDateRange(
                  provider, startDateTime, endDateTime, PageRequest.of(0, limit))
              .getContent();
    } else {
      jobs =
          processingJobRepository.findByProviderAndDateRange(provider, startDateTime, endDateTime);
    }

    return jobs.stream().map(this::convertToStatusResponse).collect(Collectors.toList());
  }

  @Transactional
  public String retryFailedProcessing(String processingId) {
    log.info("Retrying failed processing job: {}", processingId);

    Optional<ProcessingJob> jobOpt = processingJobRepository.findById(processingId);
    if (jobOpt.isEmpty()) {
      throw new IllegalArgumentException("Processing job not found: " + processingId);
    }

    ProcessingJob originalJob = jobOpt.get();

    if (originalJob.getStatus() != ProcessingStatus.FAILED) {
      throw new IllegalStateException("Cannot retry job in status: " + originalJob.getStatus());
    }

    // Create new job for retry
    String newJobId = generateProcessingJobId();
    ProcessingJob retryJob =
        ProcessingJob.builder()
            .processingId(newJobId)
            .status(ProcessingStatus.PENDING)
            .provider(originalJob.getProvider())
            .isAsynchronous(true) // Always async for retries
            .s3Prefix(originalJob.getS3Prefix())
            .maxFiles(originalJob.getMaxFiles())
            .triggeredBy("RETRY_" + processingId)
            .build();

    processingJobRepository.save(retryJob);

    // Create retry request
    ProcessingTriggerRequest retryRequest =
        ProcessingTriggerRequest.builder()
            .provider(originalJob.getProvider())
            .s3Prefix(originalJob.getS3Prefix())
            .maxFiles(originalJob.getMaxFiles())
            .asynchronous(true)
            .triggeredBy("RETRY_" + processingId)
            .build();

    // Start retry processing
    CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> executeProcessingJob(newJobId, retryRequest))
            .whenComplete(
                (result, throwable) -> {
                  activeJobs.remove(newJobId);
                  if (throwable != null) {
                    log.error("Retry processing failed for job: {}", newJobId, throwable);
                    updateJobStatus(newJobId, ProcessingStatus.FAILED, throwable.getMessage());
                  }
                });

    activeJobs.put(newJobId, future);

    log.info("Retry processing started with new job ID: {}", newJobId);
    return "Retry processing started. New job ID: " + newJobId;
  }

  private String executeProcessingJob(String processingJobId, ProcessingTriggerRequest request) {
    try {
      log.info("Starting processing execution for job: {}", processingJobId);

      // Update job status to in progress
      updateJobStatus(processingJobId, ProcessingStatus.IN_PROGRESS, null);

      // Discover files to process
      int maxFiles = request.getMaxFiles() != null ? request.getMaxFiles() : maxFilesPerRequest;
      List<S3Object> newFilesToProcess = s3Service.listNewFiles();

      if (newFilesToProcess.isEmpty()) {
        String message = "No new files found to process";
        log.info("{} for job: {}", message, processingJobId);
        updateJobStatus(processingJobId, ProcessingStatus.COMPLETED, null);
        return message + ". Job ID: " + processingJobId;
      }

      // Filter files if provider filter is specified
      if (request.getProvider() != null) {
        newFilesToProcess =
            filterS3ObjectByProvider(newFilesToProcess, request.getProvider().getDisplayName());
        log.info(
            "After provider filtering: {} files remaining for job: {}",
            newFilesToProcess.size(),
            processingJobId);
      }

      // Limit files if specified
      if (maxFiles > 0 && newFilesToProcess.size() > maxFiles) {
        newFilesToProcess = newFilesToProcess.subList(0, maxFiles);
      }

      // Update job with total files
      Optional<ProcessingJob> jobOpt = processingJobRepository.findById(processingJobId);
      if (jobOpt.isPresent()) {
        ProcessingJob job = jobOpt.get();
        job.setTotalFiles(newFilesToProcess.size());
        job.markAsStarted();
        processingJobRepository.save(job);
      }

      // Process files
      int processedCount = 0;
      int failedCount = 0;
      int totalReviews = 0;

      for (S3Object newFile : newFilesToProcess) {
        String fileName = newFile.key();
        try {
          log.debug("Processing file: {} for job: {}", fileName, processingJobId);

          if (fileTrackingService.isFileAlreadyProcessed(fileName)) {
            log.debug("File {} already processed, skipping", fileName);
            continue;
          }

          int reviewsProcessed = reviewProcessingService.processFile(fileName).intValue();
          totalReviews += reviewsProcessed;
          processedCount++;

          fileTrackingService.markProcessingCompleted(fileName, reviewsProcessed, 0);

          // Update job progress
          updateJobProgress(
              processingJobId, processedCount, failedCount, totalReviews, fileName, null);

          log.debug("Successfully processed file: {} with {} reviews", fileName, reviewsProcessed);

        } catch (Exception e) {
          failedCount++;
          log.error("Failed to process file: {} for job: {}", fileName, processingJobId, e);

          try {
            fileTrackingService.markProcessingFailed(fileName, e.getMessage());
          } catch (Exception trackingException) {
            log.error("Failed to mark file as failed: {}", fileName, trackingException);
          }

          // Update job progress
          updateJobProgress(
              processingJobId, processedCount, failedCount, totalReviews, null, fileName);
        }
      }

      // Mark job as completed
      String summary =
          String.format(
              "Processing completed for job: %s. Files processed: %d, Failed: %d, Total reviews: %d",
              processingJobId, processedCount, failedCount, totalReviews);

      updateJobStatus(processingJobId, ProcessingStatus.COMPLETED, null);
      updateProcessingMetrics(processedCount, failedCount, totalReviews);

      log.info(summary);
      return summary;

    } catch (Exception e) {
      String errorMessage =
          "Processing execution failed for job: " + processingJobId + ". Error: " + e.getMessage();
      log.error(errorMessage, e);
      updateJobStatus(processingJobId, ProcessingStatus.FAILED, e.getMessage());
      return errorMessage;
    }
  }

  @Transactional
  private void updateJobStatus(
      String processingJobId, ProcessingStatus status, String errorMessage) {
    Optional<ProcessingJob> jobOpt = processingJobRepository.findById(processingJobId);
    if (jobOpt.isPresent()) {
      ProcessingJob job = jobOpt.get();

      switch (status) {
        case IN_PROGRESS:
          job.markAsStarted();
          break;
        case COMPLETED:
          job.markAsCompleted();
          break;
        case FAILED:
          job.markAsFailed(errorMessage);
          break;
        case CANCELLED:
          job.markAsCancelled();
          break;
      }

      processingJobRepository.save(job);
    }
  }

  @Transactional
  private void updateJobProgress(
      String processingJobId,
      int processedFiles,
      int failedFiles,
      int totalReviews,
      String successFile,
      String failedFile) {
    Optional<ProcessingJob> jobOpt = processingJobRepository.findById(processingJobId);
    if (jobOpt.isPresent()) {
      ProcessingJob job = jobOpt.get();
      job.updateProgress(processedFiles, failedFiles, totalReviews);

      if (successFile != null) {
        job.addProcessedFile(successFile);
      }
      if (failedFile != null) {
        job.addFailedFile(failedFile);
      }

      processingJobRepository.save(job);
    }
  }

  @Transactional
  public void clearProcessingHistory(Integer olderThanDays) {
    int daysToKeep = olderThanDays != null ? olderThanDays : cleanupRetentionDays;
    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);

    log.info("Clearing processing history older than {} days (before {})", daysToKeep, cutoffDate);

    int deletedCount = processingJobRepository.deleteOlderThan(cutoffDate);
    log.info("Cleared {} processing history records", deletedCount);
  }

  public Map<String, Object> getSystemHealth() {
    Map<String, Object> health = new HashMap<>();

    try {
      // Overall system status
      health.put("status", "UP");
      health.put("timestamp", LocalDateTime.now());

      // S3 connectivity
      Map<String, Object> s3Health = new HashMap<>();
      try {
        s3Service.listNewFiles(); // Test S3 connectivity
        s3Health.put("status", "UP");
      } catch (Exception e) {
        s3Health.put("status", "DOWN");
        s3Health.put("error", e.getMessage());
        health.put("status", "DEGRADED");
      }
      health.put("s3", s3Health);

      // Database connectivity
      Map<String, Object> dbHealth = new HashMap<>();
      try {
        processingJobRepository.count();
        dbHealth.put("status", "UP");
      } catch (Exception e) {
        dbHealth.put("status", "DOWN");
        dbHealth.put("error", e.getMessage());
        health.put("status", "DOWN");
      }
      health.put("database", dbHealth);

      // Processing status
      Map<String, Object> processingHealth = new HashMap<>();
      processingHealth.put("activeJobs", activeJobs.size());
      processingHealth.put("schedulingPaused", isScheduledProcessingPaused.get());
      processingHealth.put("maxConcurrentFiles", maxConcurrentFiles);
      health.put("processing", processingHealth);

    } catch (Exception e) {
      health.put("status", "DOWN");
      health.put("error", e.getMessage());
    }

    return health;
  }

  public Map<String, Object> getSystemMetrics() {
    Map<String, Object> metrics = new HashMap<>();

    // Processing metrics
    metrics.put("totalProcessedFiles", totalProcessedFiles.get());
    metrics.put("totalFailedFiles", totalFailedFiles.get());
    metrics.put("totalProcessedReviews", totalProcessedReviews.get());
    metrics.put("currentlyProcessingFiles", currentlyProcessingFiles.get());

    // Database statistics
    LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
    Object[] stats = processingJobRepository.getProcessingStatistics(last24Hours);
    if (stats != null && stats.length >= 5) {
      Map<String, Object> last24HoursStats = new HashMap<>();
      last24HoursStats.put("completed", stats[0]);
      last24HoursStats.put("failed", stats[1]);
      last24HoursStats.put("inProgress", stats[2]);
      last24HoursStats.put("cancelled", stats[3]);
      last24HoursStats.put("totalReviews", stats[4]);
      metrics.put("last24Hours", last24HoursStats);
    }

    // Active jobs
    metrics.put("activeJobsCount", activeJobs.size());

    return metrics;
  }

  public Map<String, Object> validateConfiguration() {
    Map<String, Object> validation = new HashMap<>();
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // Validate S3 configuration
    try {
      s3Service.listNewFiles();
    } catch (Exception e) {
      errors.add("S3 configuration invalid: " + e.getMessage());
    }

    // Validate database
    try {
      processingJobRepository.count();
    } catch (Exception e) {
      errors.add("Database configuration invalid: " + e.getMessage());
    }

    // Validate processing parameters
    if (maxConcurrentFiles <= 0) {
      errors.add("Max concurrent files must be greater than 0");
    }

    if (maxFilesPerRequest <= 0) {
      warnings.add("Max files per request should be greater than 0");
    }

    validation.put("valid", errors.isEmpty());
    validation.put("errors", errors);
    validation.put("warnings", warnings);
    validation.put("timestamp", LocalDateTime.now());

    return validation;
  }

  public void pauseScheduledProcessing() {
    log.info("Pausing scheduled processing");
    isScheduledProcessingPaused.set(true);
  }

  public void resumeScheduledProcessing() {
    log.info("Resuming scheduled processing");
    isScheduledProcessingPaused.set(false);
  }

  public List<String> getProcessingLogs(String processingId, Integer limit) {
    // In a real implementation, this would retrieve logs from a centralized logging system
    // For now, return basic information about the processing job
    Optional<ProcessingJob> jobOpt = processingJobRepository.findById(processingId);

    if (jobOpt.isEmpty()) {
      return Arrays.asList("Processing job not found: " + processingId);
    }

    ProcessingJob job = jobOpt.get();
    List<String> logs = new ArrayList<>();

    logs.add(String.format("[%s] Processing job created: %s", job.getCreatedAt(), processingId));

    if (job.getStartTime() != null) {
      logs.add(String.format("[%s] Processing started", job.getStartTime()));
    }

    if (job.getProcessedFiles() != null && job.getProcessedFiles() > 0) {
      logs.add(String.format("Processed %d files successfully", job.getProcessedFiles()));
    }

    if (job.getFailedFiles() != null && job.getFailedFiles() > 0) {
      logs.add(String.format("Failed to process %d files", job.getFailedFiles()));
    }

    if (job.getEndTime() != null) {
      logs.add(
          String.format(
              "[%s] Processing completed with status: %s", job.getEndTime(), job.getStatus()));
    }

    if (job.getErrorMessage() != null) {
      logs.add(String.format("Error: %s", job.getErrorMessage()));
    }

    // Apply limit if specified
    if (limit != null && limit > 0 && logs.size() > limit) {
      logs = logs.subList(0, limit);
    }

    return logs;
  }

  public void clearCaches(String cacheName) {
    log.info("Clearing cache: {}", cacheName);

    if (cacheManager != null) {
      if ("all".equalsIgnoreCase(cacheName)) {
        // Clear all caches
        cacheManager
            .getCacheNames()
            .forEach(
                name -> {
                  var cache = cacheManager.getCache(name);
                  if (cache != null) {
                    cache.clear();
                    log.info("Cleared cache: {}", name);
                  }
                });
      } else {
        // Clear specific cache
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
          cache.clear();
          log.info("Cleared cache: {}", cacheName);
        } else {
          log.warn("Cache not found: {}", cacheName);
        }
      }
    } else {
      log.warn("Cache manager not available");
    }
  }

  public Map<ProviderType, Map<String, Object>> getProviderStatuses() {
    Map<ProviderType, Map<String, Object>> providerStatuses = new HashMap<>();

    for (ProviderType provider : ProviderType.values()) {
      Map<String, Object> status = new HashMap<>();

      // Get recent processing statistics for this provider
      LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
      List<ProcessingJob> recentJobs =
          processingJobRepository.findByProviderAndDateRange(
              provider, last24Hours, LocalDateTime.now());

      long completedJobs =
          recentJobs.stream().filter(job -> job.getStatus() == ProcessingStatus.COMPLETED).count();

      long failedJobs =
          recentJobs.stream().filter(job -> job.getStatus() == ProcessingStatus.FAILED).count();

      long inProgressJobs =
          recentJobs.stream()
              .filter(job -> job.getStatus() == ProcessingStatus.IN_PROGRESS)
              .count();

      int totalReviews =
          recentJobs.stream()
              .filter(job -> job.getTotalReviews() != null)
              .mapToInt(ProcessingJob::getTotalReviews)
              .sum();

      // Calculate provider health
      String healthStatus = "UP";
      if (failedJobs > 0 && completedJobs == 0) {
        healthStatus = "DOWN";
      } else if (failedJobs > completedJobs) {
        healthStatus = "DEGRADED";
      }

      status.put("health", healthStatus);
      status.put("completedJobs24h", completedJobs);
      status.put("failedJobs24h", failedJobs);
      status.put("inProgressJobs", inProgressJobs);
      status.put("totalReviews24h", totalReviews);
      status.put("lastUpdated", LocalDateTime.now());

      // Get last processing time for this provider
      Optional<ProcessingJob> lastJob =
          recentJobs.stream()
              .filter(job -> job.getEndTime() != null)
              .max(Comparator.comparing(ProcessingJob::getEndTime));

      if (lastJob.isPresent()) {
        status.put("lastProcessingTime", lastJob.get().getEndTime());
        status.put("lastProcessingStatus", lastJob.get().getStatus());
      }

      providerStatuses.put(provider, status);
    }

    return providerStatuses;
  }
}
