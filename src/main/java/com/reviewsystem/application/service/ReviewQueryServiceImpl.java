package com.reviewsystem.application.service;

import com.reviewsystem.application.dto.request.ReviewFilterRequest;
import com.reviewsystem.application.dto.response.ReviewResponse;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.Review;
import com.reviewsystem.repository.ReviewRepository;
import com.reviewsystem.repository.specification.ReviewSpecifications;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryServiceImpl implements ReviewQueryService {

  private final ReviewRepository reviewRepository;

  @Override
  public Page<ReviewResponse> getReviews(ReviewFilterRequest filterRequest, Pageable pageable) {
    var spec = ReviewSpecifications.applyFilters(filterRequest);
    Page<Review> reviewPage = reviewRepository.findAll(spec, pageable);
    return reviewPage.map(ReviewResponse::fromEntity);
  }

  @Override
  public ReviewResponse getReviewById(Long id) {
    Review review =
        reviewRepository
            .findById(id)
            .orElseThrow(() -> new RuntimeException("Review not found with ID: " + id));
    return ReviewResponse.fromEntity(review);
  }

  @Override
  public Page<ReviewResponse> getReviewsByHotelId(Long hotelId, Pageable pageable) {
    var spec =
        ReviewSpecifications.applyFilters(ReviewFilterRequest.builder().hotelId(hotelId).build());
    Page<Review> reviews = reviewRepository.findAll(spec, pageable);
    return reviews.map(ReviewResponse::fromEntity);
  }

  @Override
  public Map<String, Object> getReviewStatistics(ReviewFilterRequest filterRequest) {
    var spec = ReviewSpecifications.applyFilters(filterRequest);
    long totalReviews = reviewRepository.count(spec);
    Double averageRating = reviewRepository.findAverageRating();
    Map<String, Object> stats = new HashMap<>();
    stats.put("totalReviews", totalReviews);
    stats.put("averageRating", averageRating != null ? averageRating : 0.0);
    return stats;
  }

  @Override
  public Page<ReviewResponse> searchReviews(ReviewFilterRequest filterRequest, Pageable pageable) {
    // For now, same implementation as getReviews (using filter)
    return getReviews(filterRequest, pageable);
  }

  @Override
  public Map<String, Object> getPlatformStatistics(ReviewFilterRequest filterRequest) {
    // This method will return statistics by platform/provider
    // Implement custom repository query for aggregated platform stats
    return new HashMap<>();
  }

  @Override
  public Long getTotalReviewCount() {
    return reviewRepository.count();
  }

  @Override
  public Long getReviewCountByDateRange(LocalDate startDate, LocalDate endDate) {
    LocalDateTime start = startDate.atStartOfDay();
    LocalDateTime end = endDate.atTime(23, 59, 59);
    return reviewRepository.countByReviewDateBetween(start, end);
  }

  @Override
  public Double getAverageRating() {
    return reviewRepository.findAverageRating();
  }

  @Override
  public Long getReviewCountByProvider(ProviderType provider) {
    return reviewRepository.countByProviderCode(provider);
  }

  @Override
  public Double getAverageRatingByProvider(ProviderType provider) {
    return reviewRepository.findAverageRatingByProvider(provider);
  }
}
