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
  Optional<Review> findByProviderExternalIdAndProvider(
      String providerExternalId, Provider provider);

  boolean existsByProviderExternalIdAndProvider(String providerExternalId, Provider provider);

  Page<Review> findByHotelId(Integer hotelId, Pageable pageable);

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
  List<Review> findByTranslateSource(String translateSource);

  // Reviewer -based queries
  List<Review> findByReviewerDisplayName(String reviewerDisplayName);

  // Custom JPQL queries
  @Query("SELECT r FROM Review r WHERE r.hotelId = :hotelId AND r.rating >= :minRating")
  List<Review> findByHotelIdAndMinRating(
      @Param("hotelId") String hotelId, @Param("minRating") Double minRating);

  @Query(
      "SELECT r FROM Review r JOIN FETCH r.provider p WHERE p = :provider AND r.reviewDate > :fromDate ORDER BY r.reviewDate DESC")
  List<Review> findRecentReviewsByProvider(
      @Param("provider") Provider provider, @Param("fromDate") LocalDateTime fromDate);

  @Query("SELECT AVG(r.rating) FROM Review r WHERE r.hotelId = :hotelId")
  Double findAverageRatingByHotelId(@Param("hotelId") String hotelId);

  @Query("SELECT COUNT(r) FROM Review r WHERE r.hotelId = :hotelId")
  Long countReviewsByHotelId(@Param("hotelId") String hotelId);

  @Query(
      "SELECT r FROM Review r WHERE r.hotelId = :hotelId AND r.translateSource = :translateSource ORDER BY r.reviewDate DESC")
  Page<Review> findByHotelIdAndLanguageOrderByReviewDateDesc(
      @Param("hotelId") String hotelId,
      @Param("translateSource") String translateSource,
      Pageable pageable);

  // Native query example for complex operations
  @Query(
      value =
          "SELECT * FROM reviews r WHERE r.property_id = :hotelId AND "
              + "r.review_date >= :startDate AND r.review_date <= :endDate AND "
              + "r.rating >= :minRating ORDER BY r.review_date DESC",
      nativeQuery = true)
  List<Review> findReviewsWithComplexCriteria(
      @Param("hotelId") String hotelId,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("minRating") Double minRating);

  /** Check if a review exists by provider review ID to prevent duplicates */
  boolean existsByProviderExternalId(String providerExternalId);

  /** Check if a review exists by provider review ID to prevent duplicates */
  boolean existsByProviderReviews(Review review);

  /** Find reviews by hotel ID */
  List<Review> findByHotelId(Integer hotelId);

  /** Find reviews by provider ID */
  List<Review> findByProviderId(Long providerId);

  /** Find reviews by hotel ID and provider ID */
  List<Review> findByHotelIdAndProviderId(Integer hotelId, Long providerId);

  /** Find review by provider review ID */
  Optional<Review> findByProviderExternalId(String hotelReviewId);

  /** Find reviews created within a date range */
  @Query("SELECT r FROM Review r WHERE r.createdAt BETWEEN :startDate AND :endDate")
  List<Review> findByCreatedAtBetween(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  /** Find reviews by hotel name (case-insensitive) */
  @Query("SELECT r FROM Review r WHERE LOWER(r.hotelName) LIKE LOWER(CONCAT('%', :hotelName, '%'))")
  List<Review> findByHotelNameContainingIgnoreCase(@Param("hotelName") String hotelName);

  /** Count reviews by provider */
  @Query("SELECT COUNT(r) FROM Review r WHERE r.provider.id = :providerId")
  Long countByProviderId(@Param("providerId") Long providerId);

  /** Count reviews before cutoff */
  Long countByCreatedAtBefore(LocalDateTime dateTime);

  /** Count reviews by hotel */
  Long countByHotelId(Integer hotelId);

  /** Find recent reviews (last N days) */
  @Query("SELECT r FROM Review r WHERE r.createdAt >= :cutoffDate ORDER BY r.createdAt DESC")
  List<Review> findRecentReviews(@Param("cutoffDate") LocalDateTime cutoffDate);

  /** Find reviews with rating above threshold */
  @Query("SELECT r FROM Review r WHERE r.rating > :threshold ORDER BY r.rating DESC")
  List<Review> findHighRatedReviews(@Param("threshold") Double threshold);

  /** Find duplicate reviews based on hotel ID and review content hash */
  @Query("SELECT r FROM Review r WHERE r.hotelId = :hotelId AND r.contentHash = :contentHash")
  List<Review> findPotentialDuplicates(
      @Param("hotelId") Integer hotelId, @Param("contentHash") String contentHash);
}
