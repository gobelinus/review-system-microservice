package com.reviewsystem.domain.repository;

import com.reviewsystem.domain.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Check if a review exists by provider review ID to prevent duplicates
     */
    boolean existsByproviderReviewId(String providerReviewId);

    /**
     * Find reviews by hotel ID
     */
    List<Review> findByHotelId(Integer hotelId);

    /**
     * Find reviews by provider ID
     */
    List<Review> findByproviderId(Long providerId);

    /**
     * Find reviews by hotel ID and provider ID
     */
    List<Review> findByHotelIdAndproviderId(Integer hotelId, Long providerId);

    /**
     * Find review by provider review ID
     */
    Optional<Review> findByproviderReviewId(String providerReviewId);

    /**
     * Find reviews created within a date range
     */
    @Query("SELECT r FROM Review r WHERE r.createdAt BETWEEN :startDate AND :endDate")
    List<Review> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Find reviews by hotel name (case-insensitive)
     */
    @Query("SELECT r FROM Review r WHERE LOWER(r.hotelName) LIKE LOWER(CONCAT('%', :hotelName, '%'))")
    List<Review> findByHotelNameContainingIgnoreCase(@Param("hotelName") String hotelName);

    /**
     * Find reviews by rating range
     */
    @Query("SELECT r FROM Review r WHERE r.rating BETWEEN :minRating AND :maxRating")
    List<Review> findByRatingBetween(@Param("minRating") Double minRating,
                                     @Param("maxRating") Double maxRating);

    /**
     * Count reviews by provider
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.provider.id = :providerId")
    Long countByproviderId(@Param("providerId") Long providerId);

    /**
     * Count reviews by hotel
     */
    Long countByHotelId(Integer hotelId);

    /**
     * Find recent reviews (last N days)
     */
    @Query("SELECT r FROM Review r WHERE r.createdAt >= :cutoffDate ORDER BY r.createdAt DESC")
    List<Review> findRecentReviews(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find reviews with rating above threshold
     */
    @Query("SELECT r FROM Review r WHERE r.rating > :threshold ORDER BY r.rating DESC")
    List<Review> findHighRatedReviews(@Param("threshold") Double threshold);

    /**
     * Delete reviews by provider review ID
     */
    void deleteByproviderReviewId(String providerReviewId);

    /**
     * Find duplicate reviews based on hotel ID and review content hash
     */
    @Query("SELECT r FROM Review r WHERE r.hotelId = :hotelId AND r.contentHash = :contentHash")
    List<Review> findPotentialDuplicates(@Param("hotelId") Integer hotelId,
                                         @Param("contentHash") String contentHash);
}