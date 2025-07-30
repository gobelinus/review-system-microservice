package com.reviewsystem.domain.repository;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.entity.ProcessedFile;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Repository interface for ProcessedFile entity */
public interface ProcessedFileRepository {

  /** Save a processed file record */
  ProcessedFile save(ProcessedFile processedFile);

  /** Find by ID */
  Optional<ProcessedFile> findById(Long id);

  /** Find by S3 key and ETag for duplicate detection */
  Optional<ProcessedFile> findByS3KeyAndEtag(String s3Key, String etag);

  /** Find by S3 key only */
  Optional<ProcessedFile> findByS3Key(String s3Key);

  /** Check if file exists by S3 key and ETag */
  boolean existsByS3KeyAndEtag(String s3Key, String etag);

  /** Find all files with specific processing status */
  List<ProcessedFile> findByProcessingStatus(ProcessingStatus status);

  /** Find files by provider and status */
  List<ProcessedFile> findByProviderAndProcessingStatus(String provider, ProcessingStatus status);

  /** Find files created after specific date */
  List<ProcessedFile> findByCreatedAtAfter(LocalDateTime dateTime);

  /** Find files for cleanup (older than specified date and completed/failed) */
  List<ProcessedFile> findOldProcessedFiles(
      LocalDateTime cutoffDate, List<ProcessingStatus> statuses);

  /** Find files that have been processing for too long (stuck files) */
  List<ProcessedFile> findStuckProcessingFiles(LocalDateTime cutoffDateTime);

  /** Find recently processed files for a provider */
  List<ProcessedFile> findRecentlyProcessedFiles(
      String provider, LocalDateTime since, ProcessingStatus status);

  /** Count files by status and between times */
  long countByProcessingStatusAndProcessingCompletedAtBetween(
      ProcessingStatus status, LocalDateTime startTime, LocalDateTime endTime);

  /** Count files by status */
  long countByProcessingStatus(ProcessingStatus status);

  /** count all processed file before cutoff */
  long countByProcessingCompletedAtBefore(LocalDateTime dateTime);

  /** Count files by status and before cutoff */
  long countByProcessingStatusAndProcessingCompletedAtBefore(
      ProcessingStatus status, LocalDateTime startTime);

  /** Count files by provider and status */
  long countByProviderAndProcessingStatus(String provider, ProcessingStatus status);

  /** Delete old processed file records */
  int deleteByCreatedAtBeforeAndProcessingStatusIn(
      LocalDateTime cutoffDate, List<ProcessingStatus> statuses);

  /** Delete old processed file before cutoff */
  Long deleteByProcessingCompletedAtBefore(LocalDateTime cutoffDate);

  /** Find all records (mainly for testing) */
  List<ProcessedFile> findAll();

  /** Delete by ID */
  void deleteById(Long id);

  /** Delete all records (mainly for testing) */
  void deleteAll();

  /**
   * Find the most recently processed file ordered by processingCompletedAt timestamp in descending
   * order. Returns the latest processed file regardless of status.
   *
   * @return Optional containing the most recently processed file, or empty if no files exist
   */
  Optional<ProcessedFile> findTopByOrderByProcessingCompletedAtDesc();

  /**
   * Count all processed files for today using existing method This default method calculates
   * today's date range and uses existing functionality
   *
   * @return Number of files processed today
   */
  default long countProcessedFilesToday() {
    LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
    LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

    // Use existing method by calculating:
    // total before end of day - total before start of day = today's count
    long totalBeforeEndOfDay = countByProcessingCompletedAtBefore(endOfDay.plusNanos(1));
    long totalBeforeStartOfDay = countByProcessingCompletedAtBefore(startOfDay);

    return totalBeforeEndOfDay - totalBeforeStartOfDay;
  }

  /**
   * Count failed files for today using existing method This default method calculates today's date
   * range and uses existing functionality
   *
   * @return Number of files that failed processing today
   */
  default long countFailedFilesToday() {
    LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
    LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

    // Use existing countByProcessingStatusAndProcessingCompletedAtBetween method
    return countByProcessingStatusAndProcessingCompletedAtBetween(
        ProcessingStatus.FAILED, startOfDay, endOfDay);
  }
}
