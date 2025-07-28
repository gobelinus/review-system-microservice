package com.reviewsystem.application.service;

import com.reviewsystem.application.dto.request.ReviewFilterRequest;
import com.reviewsystem.application.dto.response.ReviewResponse;
import com.reviewsystem.common.enums.ProviderType;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewQueryService {

  Page<ReviewResponse> getReviews(ReviewFilterRequest filterRequest, Pageable pageable);

  ReviewResponse getReviewById(Long id);

  Page<ReviewResponse> getReviewsByHotelId(Long hotelId, Pageable pageable);

  Map<String, Object> getReviewStatistics(ReviewFilterRequest filterRequest);

  Page<ReviewResponse> searchReviews(ReviewFilterRequest filterRequest, Pageable pageable);

  Map<String, Object> getPlatformStatistics(ReviewFilterRequest filterRequest);

  // Metrics support methods
  Long getTotalReviewCount();

  Long getReviewCountByDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate);

  Double getAverageRating();

  Long getReviewCountByProvider(ProviderType provider);

  Double getAverageRatingByProvider(ProviderType provider);
}
