package com.reviewsystem.domain.service;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.entity.ProcessedFile;
import com.reviewsystem.domain.repository.ProcessedFileRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for tracking processed files to ensure idempotent processing */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileTrackingService {

  private final ProcessedFileRepository processedFileRepository;

  @Value("${app.file-tracking.cleanup-days:30}")
  private int cleanupRetentionDays;

  @Value("${app.file-tracking.stuck-processing-hours:2}")
  private int stuckProcessingHours;

  /**
   * Filters a list of files to return only those that haven't been processed yet.
   *
   * @param allFiles List of all file keys from S3
   * @return List of unprocessed file keys
   */
  @Transactional(readOnly = true)
  public List<String> filterUnprocessedFiles(List<String> allFiles) {
    log.info("Filtering {} files to find unprocessed ones", allFiles.size());

    if (allFiles.isEmpty()) {
      log.debug("No files provided for filtering");
      return allFiles;
    }

    // Get all processed files from the database
    List<ProcessedFile> unProcessedFiles =
        processedFileRepository.findByProcessingStatus(ProcessingStatus.PENDING);
    log.debug("Found {} already processed files in database", unProcessedFiles.size());

    // Filter out keys from list
    List<String> unprocessedFileKeys =
        unProcessedFiles.stream().map(ProcessedFile::getS3Key).collect(Collectors.toList());

    log.info(
        "Filtered result: {} unprocessed files out of {} total files",
        unprocessedFileKeys.size(),
        allFiles.size());

    return unprocessedFileKeys;
  }

  /** Check if file was already processed successfully */
  public boolean isFileAlreadyProcessed(String s3Key) {
    log.debug("Checking if file is already processed: s3Key={}", s3Key);

    Optional<ProcessedFile> existingFile = processedFileRepository.findByS3Key(s3Key);

    if (existingFile.isPresent()) {
      ProcessedFile file = existingFile.get();
      boolean isProcessed = file.isSuccessfullyProcessed();
      log.debug(
          "File found with status: {}, isProcessed: {}", file.getProcessingStatus(), isProcessed);
      return isProcessed;
    }

    log.debug("File not found in tracking records");
    return false;
  }

  /** Check if file was already processed successfully */
  public boolean isFileAlreadyProcessed(String s3Key, String etag) {
    log.debug("Checking if file is already processed: s3Key={}, etag={}", s3Key, etag);

    Optional<ProcessedFile> existingFile = processedFileRepository.findByS3KeyAndEtag(s3Key, etag);

    if (existingFile.isPresent()) {
      ProcessedFile file = existingFile.get();
      boolean isProcessed = file.isSuccessfullyProcessed();
      log.debug(
          "File found with status: {}, isProcessed: {}", file.getProcessingStatus(), isProcessed);
      return isProcessed;
    }

    log.debug("File not found in tracking records");
    return false;
  }

  /** Check if file is a duplicate (exists with same S3 key and ETag) */
  public boolean isDuplicateFile(String s3Key, String etag) {
    boolean exists = processedFileRepository.existsByS3KeyAndEtag(s3Key, etag);
    log.debug("Duplicate check for s3Key={}, etag={}: exists={}", s3Key, etag, exists);
    return exists;
  }

  /** Create tracking record for new file */
  @Transactional
  public ProcessedFile createFileTrackingRecord(
      String s3Key, String etag, Long fileSize, LocalDateTime lastModified, String provider) {
    log.info(
        "Creating file tracking record: s3Key={}, etag={}, provider={}", s3Key, etag, provider);

    // Check if record already exists
    Optional<ProcessedFile> existing = processedFileRepository.findByS3KeyAndEtag(s3Key, etag);
    if (existing.isPresent()) {
      log.warn("File tracking record already exists: {}", existing.get().getId());
      return existing.get();
    }

    ProcessedFile processedFile =
        ProcessedFile.builder()
            .s3Key(s3Key)
            .etag(etag)
            .fileSize(fileSize)
            .lastModifiedDate(lastModified)
            .provider(provider)
            .processingStatus(ProcessingStatus.PENDING)
            .build();

    ProcessedFile saved = processedFileRepository.save(processedFile);
    log.info("Created file tracking record with ID: {}", saved.getId());
    return saved;
  }

  /** Mark file processing as started */
  @Transactional
  public void markProcessingStarted(Long fileId) {
    log.info("Marking processing started for file ID: {}", fileId);

    ProcessedFile file =
        processedFileRepository
            .findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileId));

    file.markProcessingStarted();
    processedFileRepository.save(file);

    log.info("Marked processing started for file ID: {}", fileId);
  }

  /** Mark file processing as started by S3Key */
  @Transactional
  public void markProcessingStartedByS3Key(String s3Key) {
    log.info("Marking processing started for file key: {}", s3Key);

    ProcessedFile file =
        processedFileRepository
            .findByS3Key(s3Key)
            .orElseThrow(() -> new IllegalArgumentException("File not found with key: " + s3Key));

    file.markProcessingStarted();
    processedFileRepository.save(file);

    log.info("Marked processing started for file ID: {}", s3Key);
  }

  /** Mark file processing as completed */
  @Transactional
  public void markProcessingCompleted(ProcessedFile file) {
    file.markProcessingCompleted(file.getRecordsProcessed(), file.getRecordsFailed());
    processedFileRepository.save(file);

    log.info("Marked processing completed for file ID: {}", file);
  }

  /** Mark file processing as completed */
  @Transactional
  public void markProcessingCompleted(Long fileId, int recordsProcessed, int recordsFailed) {
    log.info(
        "Marking processing completed for file ID: {}, processed: {}, failed: {}",
        fileId,
        recordsProcessed,
        recordsFailed);

    ProcessedFile file =
        processedFileRepository
            .findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileId));

    file.markProcessingCompleted(recordsProcessed, recordsFailed);
    processedFileRepository.save(file);

    log.info("Marked processing completed for file ID: {}", fileId);
  }

  /** Mark file processing as completed */
  @Transactional
  public void markProcessingCompleted(String fileKey, int recordsProcessed, int recordsFailed) {
    log.info(
        "Marking processing completed for file Key: {}, processed: {}, failed: {}",
        fileKey,
        recordsProcessed,
        recordsFailed);

    ProcessedFile file =
        processedFileRepository
            .findByS3Key(fileKey)
            .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileKey));

    file.markProcessingCompleted(recordsProcessed, recordsFailed);
    processedFileRepository.save(file);

    log.info("Marked processing completed for file ID: {}", fileKey);
  }

  /** Mark file processing as failed */
  @Transactional
  public void markProcessingFailed(Long fileId, String errorMessage) {
    log.error("Marking processing failed for file ID: {}, error: {}", fileId, errorMessage);

    ProcessedFile file =
        processedFileRepository
            .findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileId));

    file.markProcessingFailed(errorMessage);
    processedFileRepository.save(file);

    log.error("Marked processing failed for file ID: {}", fileId);
  }

  /** Mark file processing as failed */
  @Transactional
  public void markProcessingFailed(String fileKey, String errorMessage) {
    log.error("Marking processing failed for file ID: {}, error: {}", fileKey, errorMessage);

    ProcessedFile file =
        processedFileRepository
            .findByS3Key(fileKey)
            .orElseThrow(() -> new IllegalArgumentException("File not found with Key: " + fileKey));

    file.markProcessingFailed(errorMessage);
    processedFileRepository.save(file);

    log.error("Marked processing failed for file Key: {}", fileKey);
  }

  /** Get file processing status */
  public Optional<ProcessingStatus> getFileProcessingStatus(String s3Key, String etag) {
    return processedFileRepository
        .findByS3KeyAndEtag(s3Key, etag)
        .map(ProcessedFile::getProcessingStatus);
  }

  /** Get all files with specific status */
  public List<ProcessedFile> getFilesByStatus(ProcessingStatus status) {
    return processedFileRepository.findByProcessingStatus(status);
  }

  /** Get processing statistics */
  public ProcessingStatistics getProcessingStatistics(String provider) {
    return ProcessingStatistics.builder()
        .totalFiles(processedFileRepository.countByProviderAndProcessingStatus(provider, null))
        .pendingFiles(
            processedFileRepository.countByProviderAndProcessingStatus(
                provider, ProcessingStatus.PENDING))
        .processingFiles(
            processedFileRepository.countByProviderAndProcessingStatus(
                provider, ProcessingStatus.IN_PROGRESS))
        .completedFiles(
            processedFileRepository.countByProviderAndProcessingStatus(
                provider, ProcessingStatus.COMPLETED))
        .failedFiles(
            processedFileRepository.countByProviderAndProcessingStatus(
                provider, ProcessingStatus.FAILED))
        .build();
  }

  /** Clean up old processed file records */
  @Transactional
  public int cleanupOldRecords() {
    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanupRetentionDays);
    List<ProcessingStatus> terminalStatuses =
        Arrays.asList(
            ProcessingStatus.COMPLETED, ProcessingStatus.FAILED, ProcessingStatus.SKIPPED);

    log.info("Cleaning up processed file records older than: {}", cutoffDate);

    int deletedCount =
        processedFileRepository.deleteByCreatedAtBeforeAndProcessingStatusIn(
            cutoffDate, terminalStatuses);

    log.info("Cleaned up {} old processed file records", deletedCount);
    return deletedCount;
  }

  /** Find and reset stuck processing files */
  @Transactional
  public List<ProcessedFile> findAndResetStuckFiles() {
    LocalDateTime cutoffDateTime = LocalDateTime.now().minusHours(stuckProcessingHours);
    List<ProcessedFile> stuckFiles =
        processedFileRepository.findStuckProcessingFiles(cutoffDateTime);

    log.warn("Found {} stuck processing files", stuckFiles.size());

    for (ProcessedFile file : stuckFiles) {
      log.warn("Resetting stuck file: ID={}, s3Key={}", file.getId(), file.getS3Key());
      file.markProcessingFailed("Processing timeout - file was stuck in processing state");
      processedFileRepository.save(file);
    }

    return stuckFiles;
  }

  /** Get recently processed files for a provider */
  public List<ProcessedFile> getRecentlyProcessedFiles(String provider, int hours) {
    LocalDateTime since = LocalDateTime.now().minusHours(hours);
    return processedFileRepository.findRecentlyProcessedFiles(
        provider, since, ProcessingStatus.COMPLETED);
  }

  /** Find file by S3 key only (may return multiple versions) */
  public Optional<ProcessedFile> findByS3Key(String s3Key) {
    return processedFileRepository.findByS3Key(s3Key);
  }

  /** Statistics class for processing metrics */
  @lombok.Data
  @lombok.Builder
  public static class ProcessingStatistics {
    private long totalFiles;
    private long pendingFiles;
    private long processingFiles;
    private long completedFiles;
    private long failedFiles;

    public double getSuccessRate() {
      if (totalFiles == 0) return 0.0;
      return (double) completedFiles / totalFiles * 100.0;
    }

    public double getFailureRate() {
      if (totalFiles == 0) return 0.0;
      return (double) failedFiles / totalFiles * 100.0;
    }
  }

  /**
   * Deletes processed file records older than the specified date.
   *
   * @param cutoffDate Date before which records should be deleted
   * @return Number of records deleted
   */
  @Transactional
  public Long deleteProcessedFilesBefore(LocalDateTime cutoffDate) {
    log.info("Starting cleanup of processed files older than: {}", cutoffDate);

    try {
      // First, count how many records will be deleted for logging
      Long countToDelete = processedFileRepository.countByProcessedAtBefore(cutoffDate);
      log.info("Found {} processed file records to delete", countToDelete);

      if (countToDelete == 0) {
        log.info("No old processed file records to delete");
        return 0L;
      }

      // Delete the old records
      Long deletedCount = processedFileRepository.deleteByProcessedAtBefore(cutoffDate);

      log.info("Successfully deleted {} old processed file records", deletedCount);
      return deletedCount;

    } catch (Exception e) {
      log.error("Error occurred while deleting old processed file records", e);
      throw new RuntimeException("Failed to delete old processed file records", e);
    }
  }

  /**
   * Gets the total number of processed files.
   *
   * @return Total count of processed files
   */
  @Transactional(readOnly = true)
  public Long getTotalProcessedFiles() {
    try {
      Long totalCount = processedFileRepository.countByProcessingStatus(ProcessingStatus.COMPLETED);
      log.debug("Total processed files count: {}", totalCount);
      return totalCount;
    } catch (Exception e) {
      log.error("Error getting total processed files count", e);
      return 0L;
    }
  }

  /**
   * Gets the number of files processed today.
   *
   * @return Count of files processed today
   */
  @Transactional(readOnly = true)
  public Long getProcessedFilesToday() {
    LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
    LocalDateTime endOfDay = startOfDay.plusDays(1);

    try {
      Long todayCount =
          processedFileRepository.countByStatusAndProcessedAtBetween(
              ProcessingStatus.COMPLETED, startOfDay, endOfDay);
      log.debug("Files processed today: {}", todayCount);
      return todayCount;
    } catch (Exception e) {
      log.error("Error getting today's processed files count", e);
      return 0L;
    }
  }
}
