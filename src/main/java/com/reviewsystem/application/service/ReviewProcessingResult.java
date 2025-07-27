// ===== ReviewProcessingResult.java =====
package com.reviewsystem.application.service;

import com.reviewsystem.common.enums.ProcessingStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewProcessingResult {
  private int processedCount;
  private int validCount;
  private int invalidCount;
  private int duplicateCount;
  private ProcessingStatus status;
  private long processingTimeMs;
  private List<String> errors;
}
