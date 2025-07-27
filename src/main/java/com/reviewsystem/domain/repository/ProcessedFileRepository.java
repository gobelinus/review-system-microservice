package com.reviewsystem.domain.repository;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.entity.ProcessedFile;
import java.time.LocalDateTime;
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

  /** Count files by status */
  long countByStatusAndProcessedAtBetween(
      ProcessingStatus status, LocalDateTime startTime, LocalDateTime endTime);

  /** Count files by status */
  long countByProcessingStatus(ProcessingStatus status);

  /** count all processed file before cutoff */
  long countByProcessedAtBefore(LocalDateTime dateTime);

  /** Count files by provider and status */
  long countByProviderAndProcessingStatus(String provider, ProcessingStatus status);

  /** Delete old processed file records */
  int deleteByCreatedAtBeforeAndProcessingStatusIn(
      LocalDateTime cutoffDate, List<ProcessingStatus> statuses);

  /** Delete old processed file before cutoff */
  Long deleteByProcessedAtBefore(LocalDateTime cutoffDate);

  /** Find all records (mainly for testing) */
  List<ProcessedFile> findAll();

  /** Delete by ID */
  void deleteById(Long id);

  /** Delete all records (mainly for testing) */
  void deleteAll();
}
