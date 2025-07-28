package com.reviewsystem.domain.entity;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Entity to track processing jobs and their status */
@Entity
@Table(name = "processing_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingJob {

  @Id
  @Column(name = "processing_id", length = 50)
  private String processingId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ProcessingStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider")
  private ProviderType provider;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Column(name = "total_files")
  private Integer totalFiles;

  @Column(name = "processed_files")
  private Integer processedFiles;

  @Column(name = "failed_files")
  private Integer failedFiles;

  @Column(name = "total_reviews")
  private Integer totalReviews;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "triggered_by", length = 100)
  private String triggeredBy;

  @Column(name = "is_asynchronous")
  private Boolean isAsynchronous;

  @Column(name = "s3_prefix", length = 500)
  private String s3Prefix;

  @Column(name = "max_files")
  private Integer maxFiles;

  @ElementCollection
  @CollectionTable(name = "processing_job_files", joinColumns = @JoinColumn(name = "processing_id"))
  @Column(name = "file_name")
  private List<String> processedFileNames = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "processing_job_failed_files",
      joinColumns = @JoinColumn(name = "processing_id"))
  @Column(name = "file_name")
  private List<String> failedFileNames = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** Calculates the duration of processing in milliseconds */
  public Long getDuration() {
    if (startTime == null) {
      return null;
    }

    LocalDateTime endTimeToUse = endTime != null ? endTime : LocalDateTime.now();
    return java.time.Duration.between(startTime, endTimeToUse).toMillis();
  }

  /** Marks the job as started */
  public void markAsStarted() {
    this.status = ProcessingStatus.IN_PROGRESS;
    this.startTime = LocalDateTime.now();
  }

  /** Marks the job as completed successfully */
  public void markAsCompleted() {
    this.status = ProcessingStatus.COMPLETED;
    this.endTime = LocalDateTime.now();
  }

  /** Marks the job as failed with error message */
  public void markAsFailed(String errorMessage) {
    this.status = ProcessingStatus.FAILED;
    this.endTime = LocalDateTime.now();
    this.errorMessage = errorMessage;
  }

  /** Marks the job as cancelled */
  public void markAsCancelled() {
    this.status = ProcessingStatus.CANCELLED;
    this.endTime = LocalDateTime.now();
  }

  /** Updates processing progress */
  public void updateProgress(int processedFiles, int failedFiles, int totalReviews) {
    this.processedFiles = processedFiles;
    this.failedFiles = failedFiles;
    this.totalReviews = totalReviews;
  }

  /** Adds a processed file to the list */
  public void addProcessedFile(String fileName) {
    if (this.processedFileNames == null) {
      this.processedFileNames = new ArrayList<>();
    }
    this.processedFileNames.add(fileName);
  }

  /** Adds a failed file to the list */
  public void addFailedFile(String fileName) {
    if (this.failedFileNames == null) {
      this.failedFileNames = new ArrayList<>();
    }
    this.failedFileNames.add(fileName);
  }
}
