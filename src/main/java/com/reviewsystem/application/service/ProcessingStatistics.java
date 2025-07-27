package com.reviewsystem.application.service;

import com.reviewsystem.common.enums.ProcessingStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Statistics about the current processing state and performance metrics. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessingStatistics {

  /** Current status of the processing system */
  private ProcessingStatus currentStatus;

  /** Total number of files processed since system start */
  private Long totalProcessedFiles;

  /** Number of files processed today */
  private Long processedFilesToday;

  /** Number of currently active processing threads */
  private Integer threadPoolActiveThreads;

  /** Current size of the processing queue */
  private Integer threadPoolQueueSize;

  /** Total Providers */
  private Long totalProviders;

  /** Total Review */
  private Long totalReviews;

  /** Total cache Size */
  private Integer cacheSize;

  /** Timestamp when statistics were collected */
  @Builder.Default private LocalDateTime timestamp = LocalDateTime.now();

  /** Last successful processing time */
  private LocalDateTime lastSuccessfulProcessing;

  /** Last error time (if any) */
  private LocalDateTime lastErrorTime;

  /** Last error message (if any) */
  private String lastErrorMessage;
}
