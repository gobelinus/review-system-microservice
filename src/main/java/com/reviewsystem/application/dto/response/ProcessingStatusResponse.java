package com.reviewsystem.application.dto.response;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingStatusResponse {

  private String id;
  private ProcessingStatus status;
  private ProviderType provider;
  private Integer totalFiles;
  private Integer processedFiles;
  private Integer totalReviews;
  private Integer processedReviews;
  private Integer failedReviews;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String errorMessage;
  private Double progressPercent;
  private String currentFile;
  private Long duration; // in milliseconds
  private List<String> processedFileNames;
  private List<String> failedFileNames;
  private String triggeredBy;
  private Boolean isAsynchronous;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
