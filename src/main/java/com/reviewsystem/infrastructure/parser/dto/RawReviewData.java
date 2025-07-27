// ===== RawReviewData.java =====
package com.reviewsystem.infrastructure.parser.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawReviewData {
  private Integer hotelId;
  private String provider;
  private String hotelName;
  private Map<String, Object> comment;
  private List<Object> overallByproviders;
  private int lineNumber;
  private String rawJson;
}
