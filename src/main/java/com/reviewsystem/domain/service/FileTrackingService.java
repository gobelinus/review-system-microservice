package com.reviewsystem.domain.service;

import com.reviewsystem.domain.entity.ProcessedFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Domain service for tracking processed files to ensure idempotent processing. */
public interface FileTrackingService {

  /**
   * Checks if a file has already been processed.
   *
   * @param fileName The name/key of the file to check
   * @return true if the file has been processed, false otherwise
   */
  boolean isFileProcessed(String fileName);

  /**
   * Marks a file as processed and saves the processing details.
   *
   * @param processedFile The processed file entity with processing details
   */
  void markFileAsProcessed(ProcessedFile processedFile);

  /**
   * Retrieves processing information for a specific file.
   *
   * @param fileName The name/key of the file
   * @return Optional containing the ProcessedFile if found, empty otherwise
   */
  Optional<ProcessedFile> getProcessedFile(String fileName);

  /**
   * Retrieves all processed files within a date range.
   *
   * @param startDate Start date for the range (inclusive)
   * @param endDate End date for the range (inclusive)
   * @return List of processed files within the date range
   */
  List<ProcessedFile> getProcessedFiles(LocalDateTime startDate, LocalDateTime endDate);

  /**
   * Deletes old processed file records to prevent database bloat.
   *
   * @param cutoffDate Files processed before this date will be deleted
   * @return Number of records deleted
   */
  int cleanupOldRecords(LocalDateTime cutoffDate);

  /**
   * Gets the total count of processed files.
   *
   * @return Total number of processed files
   */
  long getTotalProcessedFilesCount();

  /**
   * Gets the count of successfully processed files.
   *
   * @return Number of successfully processed files
   */
  long getSuccessfullyProcessedFilesCount();

  /**
   * Gets the count of failed file processing attempts.
   *
   * @return Number of failed file processing attempts
   */
  long getFailedProcessedFilesCount();
}
