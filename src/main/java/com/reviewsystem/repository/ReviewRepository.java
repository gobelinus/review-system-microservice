package com.reviewsystem.repository;

import com.reviewsystem.domain.entity.Provider;
import com.reviewsystem.domain.entity.Review;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

  // Basic finder methods
  Optional<Review> findByExternalIdAndProvider(String externalId, Provider provider);

  boolean existsByExternalIdAndProvider(String externalId, Provider provider);

  List<Review> findByPropertyId(String propertyId);

  Page<Review> findByPropertyId(String propertyId, Pageable pageable);

  List<Review> findByProvider(Provider provider);

  Page<Review> findByProvider(Provider provider, Pageable pageable);

  // Rating-based queries
  List<Review> findByRatingGreaterThanEqual(Double rating);

  List<Review> findByRatingBetween(Double minRating, Double maxRating);

  // Date-based queries
  List<Review> findByReviewDateBetween(LocalDateTime startDate, LocalDateTime endDate);

  Page<Review> findByReviewDateBetween(
      LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

  // Language-based queries
  List<Review> findByLanguage(String language);

  // Verification status
  List<Review> findByVerified(Boolean verified);

  // Custom JPQL queries
  @Query("SELECT r FROM Review r WHERE r.propertyId = :propertyId AND r.rating >= :minRating")
  List<Review> findByPropertyIdAndMinRating(
      @Param("propertyId") String propertyId, @Param("minRating") Double minRating);

  @Query(
      "SELECT r FROM Review r JOIN FETCH r.provider p WHERE p = :provider AND r.reviewDate > :fromDate ORDER BY r.reviewDate DESC")
  List<Review> findRecentReviewsByProvider(
      @Param("provider") Provider provider, @Param("fromDate") LocalDateTime fromDate);

  @Query("SELECT AVG(r.rating) FROM Review r WHERE r.propertyId = :propertyId")
  Double findAverageRatingByPropertyId(@Param("propertyId") String propertyId);

  @Query("SELECT COUNT(r) FROM Review r WHERE r.propertyId = :propertyId")
  Long countReviewsByPropertyId(@Param("propertyId") String propertyId);

  @Query(
      "SELECT r FROM Review r WHERE r.propertyId = :propertyId AND r.language = :language ORDER BY r.reviewDate DESC")
  Page<Review> findByPropertyIdAndLanguageOrderByReviewDateDesc(
      @Param("propertyId") String propertyId,
      @Param("language") String language,
      Pageable pageable);

  // Native query example for complex operations
  @Query(
      value =
          "SELECT * FROM reviews r WHERE r.property_id = :propertyId AND "
              + "r.review_date >= :startDate AND r.review_date <= :endDate AND "
              + "r.rating >= :minRating ORDER BY r.review_date DESC",
      nativeQuery = true)
  List<Review> findReviewsWithComplexCriteria(
      @Param("propertyId") String propertyId,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("minRating") Double minRating);
}
