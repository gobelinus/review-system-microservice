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
  Optional<Review> findByExternalIdAndprovider(String externalId, Provider provider);

  boolean existsByExternalIdAndprovider(String externalId, Provider provider);

  List<Review> findByhotelId(String hotelId);

  Page<Review> findByhotelId(String hotelId, Pageable pageable);

  List<Review> findByprovider(Provider provider);

  Page<Review> findByprovider(Provider provider, Pageable pageable);

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
  @Query("SELECT r FROM Review r WHERE r.hotelId = :hotelId AND r.rating >= :minRating")
  List<Review> findByhotelIdAndMinRating(
      @Param("hotelId") String hotelId, @Param("minRating") Double minRating);

  @Query(
      "SELECT r FROM Review r JOIN FETCH r.provider p WHERE p = :provider AND r.reviewDate > :fromDate ORDER BY r.reviewDate DESC")
  List<Review> findRecentReviewsByprovider(
      @Param("provider") Provider provider, @Param("fromDate") LocalDateTime fromDate);

  @Query("SELECT AVG(r.rating) FROM Review r WHERE r.hotelId = :hotelId")
  Double findAverageRatingByhotelId(@Param("hotelId") String hotelId);

  @Query("SELECT COUNT(r) FROM Review r WHERE r.hotelId = :hotelId")
  Long countReviewsByhotelId(@Param("hotelId") String hotelId);

  @Query(
      "SELECT r FROM Review r WHERE r.hotelId = :hotelId AND r.language = :language ORDER BY r.reviewDate DESC")
  Page<Review> findByhotelIdAndLanguageOrderByReviewDateDesc(
      @Param("hotelId") String hotelId, @Param("language") String language, Pageable pageable);

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
}
