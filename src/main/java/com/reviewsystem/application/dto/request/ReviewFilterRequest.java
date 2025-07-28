package com.reviewsystem.application.dto.request;

import com.reviewsystem.common.enums.ProviderType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewFilterRequest {
  private Long hotelId;
  private ProviderType platform;

  @DecimalMin(value = "0.0", message = "Minimum rating must be at least 0.0")
  @DecimalMax(value = "10.0", message = "Minimum rating must be at most 10.0")
  private Double minRating;

  @DecimalMin(value = "0.0", message = "Maximum rating must be at least 0.0")
  @DecimalMax(value = "10.0", message = "Maximum rating must be at most 10.0")
  private Double maxRating;

  private String reviewerCountry;
  private String hotelName;
  private LocalDate startDate;
  private LocalDate endDate;
  private Integer minLengthOfStay;
  private Integer maxLengthOfStay;
  private String searchQuery;
}
