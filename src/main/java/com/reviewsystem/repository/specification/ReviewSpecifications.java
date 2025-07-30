package com.reviewsystem.repository.specification;

import com.reviewsystem.application.dto.request.ReviewFilterRequest;
import com.reviewsystem.domain.entity.Review;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public class ReviewSpecifications {

  public static Specification<Review> applyFilters(ReviewFilterRequest filter) {
    return (root, query, cb) -> {
      Predicate predicate = cb.conjunction();

      if (filter == null) {
        return predicate; // no filtering, match all
      }

      if (filter.getHotelId() != null) {
        predicate = cb.and(predicate, cb.equal(root.get("hotelId"), filter.getHotelId()));
      }

      if (filter.getPlatform() != null) {
        // Assuming provider is ManyToOne and platform matches provider.code enum
        predicate =
            cb.and(predicate, cb.equal(root.get("provider").get("code"), filter.getPlatform()));
      }

      if (filter.getMinRating() != null) {
        predicate =
            cb.and(predicate, cb.greaterThanOrEqualTo(root.get("rating"), filter.getMinRating()));
      }

      if (filter.getMaxRating() != null) {
        predicate =
            cb.and(predicate, cb.lessThanOrEqualTo(root.get("rating"), filter.getMaxRating()));
      }

      if (filter.getReviewerCountry() != null && !filter.getReviewerCountry().isBlank()) {
        predicate =
            cb.and(
                predicate, cb.equal(root.get("reviewerCountryName"), filter.getReviewerCountry()));
      }

      if (filter.getHotelName() != null && !filter.getHotelName().isBlank()) {
        predicate =
            cb.and(
                predicate,
                cb.like(
                    cb.lower(root.get("hotelName")),
                    "%" + filter.getHotelName().toLowerCase() + "%"));
      }

      if (filter.getStartDate() != null) {
        predicate =
            cb.and(
                predicate,
                cb.greaterThanOrEqualTo(
                    root.get("reviewDate"), filter.getStartDate().atStartOfDay()));
      }

      if (filter.getEndDate() != null) {
        predicate =
            cb.and(
                predicate,
                cb.lessThanOrEqualTo(
                    root.get("reviewDate"), filter.getEndDate().atTime(23, 59, 59)));
      }

      if (filter.getMinLengthOfStay() != null) {
        predicate =
            cb.and(
                predicate,
                cb.greaterThanOrEqualTo(root.get("lengthOfStay"), filter.getMinLengthOfStay()));
      }

      if (filter.getMaxLengthOfStay() != null) {
        predicate =
            cb.and(
                predicate,
                cb.lessThanOrEqualTo(root.get("lengthOfStay"), filter.getMaxLengthOfStay()));
      }

      // Optional: Apply searchQuery filter on various text fields if present
      if (filter.getSearchQuery() != null && !filter.getSearchQuery().isBlank()) {
        String pattern = "%" + filter.getSearchQuery().toLowerCase() + "%";
        Predicate searchPredicate = cb.disjunction();
        searchPredicate =
            cb.or(
                searchPredicate,
                cb.like(cb.lower(root.get("reviewComments")), pattern),
                cb.like(cb.lower(root.get("reviewTitle")), pattern),
                cb.like(cb.lower(root.get("hotelName")), pattern));
        predicate = cb.and(predicate, searchPredicate);
      }

      return predicate;
    };
  }
}
