package com.reviewsystem.domain.entity;

import com.reviewsystem.common.enums.ProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Entity to track processed files from S3 to ensure idempotent processing
 */
@Entity
@Table(name = "processed_files",
        indexes = {
                @Index(name = "idx_processed_files_s3_key", columnList = "s3_key"),
                @Index(name = "idx_processed_files_status", columnList = "processing_status"),
                @Index(name = "idx_processed_files_created_at", columnList = "created_at"),
                @Index(name = "idx_processed_files_last_modified", columnList = "last_modified_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_processed_files_s3_key_etag",
                        columnNames = {"s3_key", "etag"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProcessedFile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "S3 key cannot be blank")
  @Size(max = 1024, message = "S3 key cannot exceed 1024 characters")
  @Column(name = "s3_key", nullable = false, length = 1024)
  private String s3Key;

  @NotBlank(message = "ETag cannot be blank")
  @Size(max = 255, message = "ETag cannot exceed 255 characters")
  @Column(name = "etag", nullable = false)
  private String etag;

  @NotNull(message = "File size cannot be null")
  @Positive(message = "File size must be positive")
  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "last_modified_date")
  private LocalDateTime lastModifiedDate;

  @NotNull(message = "Processing status cannot be null")
  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false)
  @Builder.Default
  private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

  @Column(name = "records_processed")
  private Integer recordsProcessed;

  @Column(name = "records_failed")
  private Integer recordsFailed;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  @NotNull(message = "provider cannot be null")
  @Size(max = 50, message = "provider cannot exceed 50 characters")
  @Column(name = "provider", nullable = false, length = 50)
  private String provider;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Column(name = "processing_started_at")
  private LocalDateTime processingStartedAt;

  @Column(name = "processing_completed_at")
  private LocalDateTime processingCompletedAt;

  /**
   * Business logic method to check if file was already processed successfully
   */
  public boolean isSuccessfullyProcessed() {
    return ProcessingStatus.COMPLETED.equals(this.processingStatus);
  }

  /**
   * Business logic method to check if file processing failed
   */
  public boolean isProcessingFailed() {
    return ProcessingStatus.FAILED.equals(this.processingStatus);
  }

  /**
   * Business logic method to check if file is currently being processed
   */
  public boolean isProcessing() {
    return ProcessingStatus.IN_PROGRESS.equals(this.processingStatus);
  }

  /**
   * Business logic method to mark processing as started
   */
  public void markProcessingStarted() {
    this.processingStatus = ProcessingStatus.IN_PROGRESS;
    this.processingStartedAt = LocalDateTime.now();
    this.errorMessage = null;
  }

  /**
   * Business logic method to mark processing as completed
   */
  public void markProcessingCompleted(int recordsProcessed, int recordsFailed) {
    this.processingStatus = ProcessingStatus.COMPLETED;
    this.processingCompletedAt = LocalDateTime.now();
    this.recordsProcessed = recordsProcessed;
    this.recordsFailed = recordsFailed;
    this.errorMessage = null;
  }

  /**
   * Business logic method to mark processing as failed
   */
  public void markProcessingFailed(String errorMessage) {
    this.processingStatus = ProcessingStatus.FAILED;
    this.processingCompletedAt = LocalDateTime.now();
    this.errorMessage = errorMessage != null && errorMessage.length() > 2000
            ? errorMessage.substring(0, 2000)
            : errorMessage;
  }

  /**
   * Business logic method to calculate processing duration
   */
  public Long getProcessingDurationMillis() {
    if (processingStartedAt == null) {
      return null;
    }

    LocalDateTime endTime = processingCompletedAt != null
            ? processingCompletedAt
            : LocalDateTime.now();

    return java.time.Duration.between(processingStartedAt, endTime).toMillis();
  }

  /**
   * Business logic method to check if file is duplicate based on S3 key and ETag
   */
  public boolean isDuplicateOf(String s3Key, String etag) {
    return this.s3Key.equals(s3Key) && this.etag.equals(etag);
  }
}