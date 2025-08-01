package com.reviewsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.Provider;
import com.reviewsystem.domain.entity.Review;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test-postgres")
@DisplayName("ReviewRepository Unit Tests")
class ReviewRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private ReviewRepository reviewRepository;

  @Autowired private ProviderRepository providerRepository;

  private Provider testprovider;
  private Review testReview;

  @BeforeEach
  void setUp() {
    // reviewRepository.deleteAll();
    // providerRepository.deleteAll();
    // Create test provider
    testprovider =
        Provider.builder()
            .code(ProviderType.AGODA)
            .name("Agoda.com")
            .description("Agoda travel provider")
            .active(true)
            .build();
    testprovider = entityManager.persistAndFlush(testprovider);
    // Create test review
    testReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("John Doe")
            .rating(4.5)
            .reviewComments("Great hotel with excellent service!")
            .reviewDate(LocalDateTime.now().minusDays(1))
            .translateSource("en")
            .build();
  }

  @Test
  @DisplayName("Should save and find review by ID")
  void shouldSaveAndFindReviewById() {
    // When
    Review savedReview = reviewRepository.save(testReview);
    Optional<Review> foundReview = reviewRepository.findById(savedReview.getId());

    // Then
    assertThat(foundReview).isPresent();
    assertThat(foundReview.get().getProvider()).isEqualTo(testprovider);
    assertThat(foundReview.get().getHotelId()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should find review by external ID and provider")
  void shouldFindByExternalReviewIdAndprovider() {
    // Given
    reviewRepository.save(testReview);

    // When
    Optional<Review> found =
        reviewRepository.findByProviderExternalIdAndProvider("AGD123456", testprovider);

    // Then
    assertThat(found).isPresent();
    assertThat(found.get().getReviewerDisplayName()).isEqualTo("John Doe");
  }

  @Test
  @DisplayName("Should check existence by external ID and provider")
  void shouldCheckExistenceByExternalReviewIdAndprovider() {
    // Given
    reviewRepository.save(testReview);

    // When
    boolean exists =
        reviewRepository.existsByProviderExternalIdAndProvider("AGD123456", testprovider);
    boolean notExists =
        reviewRepository.existsByProviderExternalIdAndProvider("NONEXISTENT", testprovider);

    // Then
    assertThat(exists).isTrue();
    assertThat(notExists).isFalse();
  }

  @Test
  @DisplayName("Should find reviews by hotel ID")
  void shouldFindReviewsByHotelId() {
    // Given
    Review review2 =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("Jane Smith")
            .rating(3.0)
            .reviewComments("Good location, clean rooms")
            .reviewDate(LocalDateTime.now().minusDays(2))
            .build();

    reviewRepository.save(testReview);
    reviewRepository.save(review2);

    // When
    List<Review> reviews = reviewRepository.findByHotelId(1);

    // Then
    assertThat(reviews).hasSize(2);
    assertThat(reviews).extracting(Review::getHotelId).containsOnly(1);
  }

  @Test
  @DisplayName("Should find reviews by hotel ID with pagination")
  void shouldFindReviewsByHotelIdWithPagination() {
    // Given
    for (int i = 0; i < 5; i++) {
      Review review =
          Review.builder()
              .provider(testprovider)
              .hotelId(1)
              .reviewerDisplayName("Reviewer " + i)
              .rating(4.0)
              .reviewComments("Review text " + i)
              .reviewDate(LocalDateTime.now().minusDays(i))
              .build();
      reviewRepository.save(review);
    }

    // When
    Pageable pageable = PageRequest.of(0, 3, Sort.by("reviewDate").descending());
    Page<Review> reviewPage = reviewRepository.findByHotelId(1, pageable);

    // Then
    assertThat(reviewPage.getContent()).hasSize(3);
    assertThat(reviewPage.getTotalElements()).isEqualTo(5);
    assertThat(reviewPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should find reviews by provider")
  void shouldFindReviewsByprovider() {
    // Given
    Provider bookingprovider =
        Provider.builder().code(ProviderType.BOOKING).name("Booking.com").active(true).build();
    bookingprovider = entityManager.persistAndFlush(bookingprovider);

    Review bookingReview =
        Review.builder()
            .provider(bookingprovider)
            .hotelId(2)
            .reviewerDisplayName("Mike Johnson")
            .rating(5.0)
            .reviewComments("Excellent experience!")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview);
    reviewRepository.save(bookingReview);

    // When
    List<Review> agodaReviews = reviewRepository.findByProvider(testprovider);
    List<Review> bookingReviews = reviewRepository.findByProvider(bookingprovider);

    // Then
    assertThat(agodaReviews).hasSize(1);
    assertThat(bookingReviews).hasSize(1);
    assertThat(agodaReviews.get(0).getProvider()).isEqualTo(testprovider);
    assertThat(bookingReviews.get(0).getProvider()).isEqualTo(bookingprovider);
  }

  @Test
  @DisplayName("Should find reviews by rating range")
  void shouldFindReviewsByRatingRange() {
    // Given
    Review lowRatingReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("Critic User")
            .rating(2.0)
            .reviewComments("Not satisfied")
            .reviewDate(LocalDateTime.now())
            .build();

    Review highRatingReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("Happy User")
            .rating(5.0)
            .reviewComments("Amazing!")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview); // 4.5 rating
    reviewRepository.save(lowRatingReview); // 1.0 rating
    reviewRepository.save(highRatingReview); // 5.0 rating

    // When
    List<Review> highRatedReviews = reviewRepository.findByRatingGreaterThanEqual(4.0);
    List<Review> midRangeReviews = reviewRepository.findByRatingBetween(2.0, 3.5);

    // Then
    assertThat(highRatedReviews).hasSize(2);
    assertThat(midRangeReviews).hasSize(1);
    assertThat(midRangeReviews.get(0).getRating()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("Should find reviews by date range")
  void shouldFindReviewsByDateRange() {
    // Given
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime yesterday = now.minusDays(1);
    LocalDateTime weekAgo = now.minusDays(7);

    Review recentReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("Recent User")
            .rating(4.0)
            .reviewComments("Recent review")
            .reviewDate(yesterday)
            .build();

    Review oldReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("Old User")
            .rating(3.0)
            .reviewComments("Old review")
            .reviewDate(weekAgo)
            .build();

    reviewRepository.save(recentReview);
    reviewRepository.save(oldReview);

    // When
    List<Review> recentReviews = reviewRepository.findByReviewDateBetween(now.minusDays(3), now);

    // Then
    assertThat(recentReviews).hasSize(1);
    // assertThat(recentReviews.get(0).getExternalId()).isEqualTo("AGD333");
  }

  @Test
  @DisplayName("Should find reviews by translateSource code")
  void shouldFindReviewsByLanguageCode() {
    // Given
    Review englishReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("English User")
            .rating(4.0)
            .reviewComments("Great place!")
            .reviewDate(LocalDateTime.now())
            .translateSource("en")
            .build();

    Review frenchReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("French User")
            .rating(3.5)
            .reviewComments("Très bien!")
            .reviewDate(LocalDateTime.now())
            .translateSource("fr")
            .build();

    reviewRepository.save(englishReview);
    reviewRepository.save(frenchReview);

    // When
    List<Review> englishReviews = reviewRepository.findByTranslateSource("en");
    List<Review> frenchReviews = reviewRepository.findByTranslateSource("fr");

    // Then
    assertThat(englishReviews).hasSize(1);
    assertThat(frenchReviews).hasSize(1);
    assertThat(englishReviews.get(0).getReviewComments()).isEqualTo("Great place!");
    assertThat(frenchReviews.get(0).getReviewComments()).isEqualTo("Très bien!");
  }

  @Test
  @DisplayName("Should find reviews by verification status")
  void shouldFindReviewsByVerificationStatus() {
    // Given
    Review verifiedReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("Verified User")
            .rating(4.0)
            .reviewComments("Verified review")
            .reviewDate(LocalDateTime.now())
            .build();

    Review unverifiedReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("Unverified User")
            .rating(3.0)
            .reviewComments("Unverified review")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(verifiedReview);
    reviewRepository.save(unverifiedReview);

    // When
    List<Review> verifiedReviews = reviewRepository.findByReviewerDisplayName("Verified User");
    List<Review> unverifiedReviews = reviewRepository.findByReviewerDisplayName("Unverified User");

    // Then
    assertThat(verifiedReviews).hasSize(1);
    assertThat(unverifiedReviews).hasSize(1);
  }

  @Test
  @DisplayName("Should execute custom query for hotel reviews with minimum rating")
  void shouldExecuteCustomQueryForHotelReviewsWithMinRating() {
    // Given
    Review lowRatingReview =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("Low Rating User")
            .rating(2.0)
            .reviewComments("Could be better")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview); // 4.5 rating
    reviewRepository.save(lowRatingReview); // 2.0 rating

    // When
    List<Review> goodReviews = reviewRepository.findByHotelIdAndMinRating("HOTEL001", 3.0);

    // Then
    assertThat(goodReviews).hasSize(1);
    assertThat(goodReviews.get(0).getRating()).isGreaterThanOrEqualTo(3.0);
  }

  @Test
  @DisplayName("Should calculate average rating for hotel")
  void shouldCalculateAverageRatingForHotel() {
    // Given
    Review review2 =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("User 2")
            .rating(4.0)
            .reviewComments("Okay experience")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview); // 4.5 rating
    reviewRepository.save(review2); // 4.0 rating
    // Average should be 4.25

    // When
    Double averageRating = reviewRepository.findAverageRatingByHotelId("HOTEL001");

    // Then
    assertThat(averageRating).isEqualTo(4.25);
  }

  @Test
  @DisplayName("Should count reviews by hotel ID")
  void shouldCountReviewsByHotelId() {
    // Given
    Review review2 =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("User 2")
            .rating(3.0)
            .reviewComments("Good stay")
            .reviewDate(LocalDateTime.now())
            .build();

    Review review3 =
        Review.builder()
            .provider(testprovider)
            .hotelId(1)
            .reviewerDisplayName("User 3")
            .rating(4.0)
            .reviewComments("Different hotel")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview);
    reviewRepository.save(review2);
    reviewRepository.save(review3);

    // When
    Long hotel1Count = reviewRepository.countReviewsByHotelId("HOTEL001");
    Long hotel2Count = reviewRepository.countReviewsByHotelId("HOTEL002");

    // Then
    assertThat(hotel1Count).isEqualTo(2);
    assertThat(hotel2Count).isEqualTo(1);
  }

  @Test
  @DisplayName("Should handle pagination and sorting correctly")
  void shouldHandlePaginationAndSortingCorrectly() {
    // Given
    for (int i = 0; i < 10; i++) {
      Review review =
          Review.builder()
              .provider(testprovider)
              .hotelId(1)
              .reviewerDisplayName("User " + i)
              .rating(0.5 + i * 0.5)
              .reviewComments("Review " + i)
              .reviewDate(LocalDateTime.now().minusDays(i))
              .build();
      reviewRepository.save(review);
    }

    // When - Sort by rating descending, page 1 (size 3)
    Pageable pageable = PageRequest.of(1, 3, Sort.by("rating").descending());
    Page<Review> reviewPage = reviewRepository.findByHotelId(1, pageable);

    // Then
    assertThat(reviewPage.getContent()).hasSize(3);
    assertThat(reviewPage.getNumber()).isEqualTo(1); // Second page (0-indexed)
    assertThat(reviewPage.getTotalElements()).isEqualTo(10);
    assertThat(reviewPage.getTotalPages()).isEqualTo(4); // Ceiling of 10/3

    // Verify sorting - should be sorted by rating descending
    List<Double> ratings = reviewPage.getContent().stream().map(Review::getRating).toList();

    for (int i = 0; i < ratings.size() - 1; i++) {
      assertThat(ratings.get(i)).isGreaterThanOrEqualTo(ratings.get(i + 1));
    }
  }

  @Test
  @DisplayName("Should validate entity constraints")
  void shouldValidateEntityConstraints() {
    // When & Then - Missing required fields should throw ConstraintViolationException
    assertThatThrownBy(
            () -> {
              Review invalidReview = Review.builder().build();
              invalidReview.setProviderExternalId(""); // Blank external ID
              reviewRepository.saveAndFlush(invalidReview);
            })
        .isInstanceOf(ConstraintViolationException.class);

    assertThatThrownBy(
            () -> {
              Review invalidReview =
                  Review.builder()
                      .provider(testprovider)
                      .hotelId(1)
                      .reviewerDisplayName("Valid Name")
                      .rating(15.0) // Rating too high
                      .reviewComments("Valid text")
                      .reviewDate(LocalDateTime.now())
                      .build();
              reviewRepository.saveAndFlush(invalidReview);
            })
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("Should handle unique constraint on external ID and provider")
  void shouldHandleUniqueConstraintOnExternalIdAndprovider() {
    // Given
    reviewRepository.save(testReview);

    // When & Then - Duplicate external ID with same provider should fail
    Review duplicateReview =
        Review.builder()
            .provider(testprovider) // Same provider
            .hotelId(2)
            .reviewerDisplayName("Another User")
            .rating(3.0)
            .reviewComments("Different review text")
            .reviewDate(LocalDateTime.now())
            .build();

    assertThatThrownBy(() -> reviewRepository.saveAndFlush(duplicateReview))
        .satisfiesAnyOf(
            throwable ->
                assertThat(throwable)
                    .hasRootCauseInstanceOf(
                        org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class),
            throwable ->
                assertThat(throwable)
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class));
  }

  @Test
  @DisplayName("Should allow same external ID with different providers")
  void shouldAllowSameExternalIdWithDifferentproviders() {
    // Given
    Provider bookingprovider =
        Provider.builder().code(ProviderType.BOOKING).name("Booking.com").active(true).build();
    bookingprovider = entityManager.persistAndFlush(bookingprovider);

    reviewRepository.save(testReview);

    // When
    Review sameExternalIdDifferentprovider =
        Review.builder()
            .provider(bookingprovider) // Different provider
            .hotelId(2)
            .reviewerDisplayName("Another User")
            .rating(3.0)
            .reviewComments("Different review text")
            .reviewDate(LocalDateTime.now())
            .build();

    Review savedReview = reviewRepository.save(sameExternalIdDifferentprovider);

    // Then
    assertThat(savedReview.getId()).isNotNull();
    assertThat(reviewRepository.count()).isEqualTo(2);
  }
}
