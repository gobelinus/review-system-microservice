package com.reviewsystem.application.dto.response;

import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.Review;
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

  public static ReviewResponse fromEntity(Review review) {
    if (review == null) {
      return null;
    }
    ReviewResponse response = new ReviewResponse();

    response.setId(review.getId());
    // Mapping Long vs Integer hotelIds - converting Integer to Long safely
    response.setHotelId(review.getHotelId() != null ? review.getHotelId().longValue() : null);
    response.setHotelName(review.getHotelName());

    // Platform: Assuming Review.provider.code is of type ProviderType enum
    response.setPlatform(review.getProvider() != null ? review.getProvider().getCode() : null);

    response.setRating(review.getRating());
    response.setRatingText(review.getRatingText());
    response.setReviewTitle(review.getReviewTitle());
    response.setReviewComments(review.getReviewComments());
    response.setReviewDate(review.getReviewDate());

    response.setReviewerCountry(review.getReviewerCountryName());
    response.setRoomTypeName(review.getRoomTypeName());
    response.setLengthOfStay(review.getLengthOfStay());
    response.setReviewerDisplayName(review.getReviewerDisplayName());
    response.setReviewNegatives(review.getReviewNegatives());
    response.setReviewPositives(review.getReviewPositives());

    return response;
  }
}
