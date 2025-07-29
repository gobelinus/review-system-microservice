package com.reviewsystem.application.dto.response;

import com.reviewsystem.common.enums.ProviderType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

  private Long id;
  private Long hotelId;
  private String hotelName;
  private ProviderType platform;
  private Double rating;
  private String ratingText;
  private String reviewTitle;
  private String reviewComments;
  private LocalDateTime reviewDate;
  private String reviewerCountry;
  private String roomTypeName;
  private Integer lengthOfStay;
  private String reviewerDisplayName;
  private String reviewNegatives;
  private String reviewPositives;
}
