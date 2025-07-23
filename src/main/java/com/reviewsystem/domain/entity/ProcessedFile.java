package com.reviewsystem.domain.entity;

import com.reviewsystem.common.enums.ProcessingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a processed file tracking record. Used to implement idempotent file
 * processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "processed_files",
    indexes = {
      @Index(name = "idx_processed_files_file_name", columnList = "fileName", unique = true),
      @Index(name = "idx_processed_files_status", columnList = "status"),
      @Index(name = "idx_processed_files_processed_at", columnList = "processedAt")
    })
public class ProcessedFile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Name/key of the file (without path). */
  @NotBlank(message = "File name cannot be blank")
  @Column(name = "file_name", nullable = false, unique = true, length = 500)
  private String fileName;

  /** Full path/key of the file in S3. */
  @NotBlank(message = "File path cannot be blank")
  @Column(name = "file_path", nullable = false, length = 1000)
  private String filePath;

  /** Size of the file in bytes. */
  @NotNull(message = "File size cannot be null")
  @PositiveOrZero(message = "File size must be zero or positive")
  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  /** Processing status of the file. */
  @NotNull(message = "Processing status cannot be null")
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProcessingStatus status;

  /** Timestamp when the file was processed. */
  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  /** Number of records successfully processed from the file. */
  @PositiveOrZero(message = "Records processed must be zero or positive")
  @Column(name = "records_processed")
  private Integer recordsProcessed;

  /** Number of records skipped due to validation or other issues. */
  @PositiveOrZero(message = "Records skipped must be zero or positive")
  @Column(name = "records_skipped")
  private Integer recordsSkipped;

  /** Total number of records in the file. */
  @PositiveOrZero(message = "Total records must be zero or positive")
  @Column(name = "total_records")
  private Integer totalRecords;

  /** Error message if processing failed. */
  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  /** Processing duration in milliseconds. */
  @PositiveOrZero(message = "Processing duration must be zero or positive")
  @Column(name = "processing_duration_ms")
  private Long processingDurationMs;

  /** MD5 checksum of the file (for integrity verification). */
  @Column(name = "checksum", length = 32)
  private String checksum;

  /** Record creation timestamp. */
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** Record last update timestamp. */
  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** Pre-persist hook to set processedAt if not already set. */
  @PrePersist
  public void prePersist() {
    if (processedAt == null) {
      processedAt = LocalDateTime.now();
    }
    if (totalRecords == null && recordsProcessed != null && recordsSkipped != null) {
      totalRecords = recordsProcessed + recordsSkipped;
    }
  }

  /** Pre-update hook to update totalRecords. */
  @PreUpdate
  public void preUpdate() {
    if (totalRecords == null && recordsProcessed != null && recordsSkipped != null) {
      totalRecords = recordsProcessed + recordsSkipped;
    }
  }

  /**
   * Checks if the file processing was successful.
   *
   * @return true if status is COMPLETED, false otherwise
   */
  public boolean isSuccessful() {
    return ProcessingStatus.COMPLETED.equals(status);
  }

  /**
   * Checks if the file processing failed.
   *
   * @return true if status is FAILED, false otherwise
   */
  public boolean isFailed() {
    return ProcessingStatus.FAILED.equals(status);
  }

  /**
   * Checks if the file is currently being processed.
   *
   * @return true if status is IN_PROGRESS, false otherwise
   */
  public boolean isInProgress() {
    return ProcessingStatus.IN_PROGRESS.equals(status);
  }
}
