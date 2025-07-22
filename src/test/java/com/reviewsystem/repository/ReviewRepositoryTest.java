package com.reviewsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
@ActiveProfiles("test")
@DisplayName("ReviewRepository Unit Tests")
class ReviewRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private ReviewRepository reviewRepository;

  @Autowired private ProviderRepository providerRepository;

  private Provider testProvider;
  private Review testReview;

  @BeforeEach
  void setUp() {
    // reviewRepository.deleteAll();
    // providerRepository.deleteAll();
    // Create test provider
    testProvider =
        Provider.builder()
            .code("AGODA")
            .name("Agoda.com")
            .description("Agoda travel platform")
            .active(true)
            .build();
    testProvider = entityManager.persistAndFlush(testProvider);
    // Create test review
    testReview =
        Review.builder()
            .externalId("AGD123456")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("John Doe")
            .rating(4.5)
            .reviewText("Great hotel with excellent service!")
            .reviewDate(LocalDateTime.now().minusDays(1))
            .language("en")
            .verified(true)
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
    assertThat(foundReview.get().getExternalId()).isEqualTo("AGD123456");
    assertThat(foundReview.get().getProvider()).isEqualTo(testProvider);
    assertThat(foundReview.get().getPropertyId()).isEqualTo("HOTEL001");
  }

  @Test
  @DisplayName("Should find review by external ID and provider")
  void shouldFindByExternalReviewIdAndProvider() {
    // Given
    reviewRepository.save(testReview);

    // When
    Optional<Review> found =
        reviewRepository.findByExternalIdAndProvider("AGD123456", testProvider);

    // Then
    assertThat(found).isPresent();
    assertThat(found.get().getGuestName()).isEqualTo("John Doe");
  }

  @Test
  @DisplayName("Should check existence by external ID and provider")
  void shouldCheckExistenceByExternalReviewIdAndProvider() {
    // Given
    reviewRepository.save(testReview);

    // When
    boolean exists = reviewRepository.existsByExternalIdAndProvider("AGD123456", testProvider);
    boolean notExists = reviewRepository.existsByExternalIdAndProvider("NONEXISTENT", testProvider);

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
            .externalId("AGD789012")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("Jane Smith")
            .rating(3.0)
            .reviewText("Good location, clean rooms")
            .reviewDate(LocalDateTime.now().minusDays(2))
            .build();

    reviewRepository.save(testReview);
    reviewRepository.save(review2);

    // When
    List<Review> reviews = reviewRepository.findByPropertyId("HOTEL001");

    // Then
    assertThat(reviews).hasSize(2);
    assertThat(reviews).extracting(Review::getPropertyId).containsOnly("HOTEL001");
  }

  @Test
  @DisplayName("Should find reviews by hotel ID with pagination")
  void shouldFindReviewsByHotelIdWithPagination() {
    // Given
    for (int i = 0; i < 5; i++) {
      Review review =
          Review.builder()
              .externalId("AGD" + i)
              .provider(testProvider)
              .propertyId("HOTEL001")
              .guestName("Reviewer " + i)
              .rating(4.0)
              .reviewText("Review text " + i)
              .reviewDate(LocalDateTime.now().minusDays(i))
              .build();
      reviewRepository.save(review);
    }

    // When
    Pageable pageable = PageRequest.of(0, 3, Sort.by("reviewDate").descending());
    Page<Review> reviewPage = reviewRepository.findByPropertyId("HOTEL001", pageable);

    // Then
    assertThat(reviewPage.getContent()).hasSize(3);
    assertThat(reviewPage.getTotalElements()).isEqualTo(5);
    assertThat(reviewPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should find reviews by provider")
  void shouldFindReviewsByProvider() {
    // Given
    Provider bookingProvider =
        Provider.builder().code("BOOKING").name("Booking.com").active(true).build();
    bookingProvider = entityManager.persistAndFlush(bookingProvider);

    Review bookingReview =
        Review.builder()
            .externalId("BKG123")
            .provider(bookingProvider)
            .propertyId("HOTEL002")
            .guestName("Mike Johnson")
            .rating(5.0)
            .reviewText("Excellent experience!")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview);
    reviewRepository.save(bookingReview);

    // When
    List<Review> agodaReviews = reviewRepository.findByProvider(testProvider);
    List<Review> bookingReviews = reviewRepository.findByProvider(bookingProvider);

    // Then
    assertThat(agodaReviews).hasSize(1);
    assertThat(bookingReviews).hasSize(1);
    assertThat(agodaReviews.get(0).getProvider()).isEqualTo(testProvider);
    assertThat(bookingReviews.get(0).getProvider()).isEqualTo(bookingProvider);
  }

  @Test
  @DisplayName("Should find reviews by rating range")
  void shouldFindReviewsByRatingRange() {
    // Given
    Review lowRatingReview =
        Review.builder()
            .externalId("AGD111")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("Critic User")
            .rating(2.0)
            .reviewText("Not satisfied")
            .reviewDate(LocalDateTime.now())
            .build();

    Review highRatingReview =
        Review.builder()
            .externalId("AGD222")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("Happy User")
            .rating(5.0)
            .reviewText("Amazing!")
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
            .externalId("AGD333")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("Recent User")
            .rating(4.0)
            .reviewText("Recent review")
            .reviewDate(yesterday)
            .build();

    Review oldReview =
        Review.builder()
            .externalId("AGD444")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("Old User")
            .rating(3.0)
            .reviewText("Old review")
            .reviewDate(weekAgo)
            .build();

    reviewRepository.save(recentReview);
    reviewRepository.save(oldReview);

    // When
    List<Review> recentReviews = reviewRepository.findByReviewDateBetween(now.minusDays(3), now);

    // Then
    assertThat(recentReviews).hasSize(1);
    assertThat(recentReviews.get(0).getExternalId()).isEqualTo("AGD333");
  }

  @Test
  @DisplayName("Should find reviews by language code")
  void shouldFindReviewsByLanguageCode() {
    // Given
    Review englishReview =
        Review.builder()
            .externalId("AGD555")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("English User")
            .rating(4.0)
            .reviewText("Great place!")
            .reviewDate(LocalDateTime.now())
            .language("en")
            .build();

    Review frenchReview =
        Review.builder()
            .externalId("AGD666")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("French User")
            .rating(3.5)
            .reviewText("Très bien!")
            .reviewDate(LocalDateTime.now())
            .language("fr")
            .build();

    reviewRepository.save(englishReview);
    reviewRepository.save(frenchReview);

    // When
    List<Review> englishReviews = reviewRepository.findByLanguage("en");
    List<Review> frenchReviews = reviewRepository.findByLanguage("fr");

    // Then
    assertThat(englishReviews).hasSize(1);
    assertThat(frenchReviews).hasSize(1);
    assertThat(englishReviews.get(0).getReviewText()).isEqualTo("Great place!");
    assertThat(frenchReviews.get(0).getReviewText()).isEqualTo("Très bien!");
  }

  @Test
  @DisplayName("Should find reviews by verification status")
  void shouldFindReviewsByVerificationStatus() {
    // Given
    Review verifiedReview =
        Review.builder()
            .externalId("AGD777")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("Verified User")
            .rating(4.0)
            .reviewText("Verified review")
            .reviewDate(LocalDateTime.now())
            .verified(true)
            .build();

    Review unverifiedReview =
        Review.builder()
            .externalId("AGD888")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("Unverified User")
            .rating(3.0)
            .reviewText("Unverified review")
            .reviewDate(LocalDateTime.now())
            .verified(false)
            .build();

    reviewRepository.save(verifiedReview);
    reviewRepository.save(unverifiedReview);

    // When
    List<Review> verifiedReviews = reviewRepository.findByVerified(true);
    List<Review> unverifiedReviews = reviewRepository.findByVerified(false);

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
            .externalId("AGD999")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("Low Rating User")
            .rating(2.0)
            .reviewText("Could be better")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview); // 4.5 rating
    reviewRepository.save(lowRatingReview); // 2.0 rating

    // When
    List<Review> goodReviews = reviewRepository.findByPropertyIdAndMinRating("HOTEL001", 3.0);

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
            .externalId("AGD101")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("User 2")
            .rating(4.0)
            .reviewText("Okay experience")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview); // 4.5 rating
    reviewRepository.save(review2); // 4.0 rating
    // Average should be 4.25

    // When
    Double averageRating = reviewRepository.findAverageRatingByPropertyId("HOTEL001");

    // Then
    assertThat(averageRating).isEqualTo(4.25);
  }

  @Test
  @DisplayName("Should count reviews by hotel ID")
  void shouldCountReviewsByHotelId() {
    // Given
    Review review2 =
        Review.builder()
            .externalId("AGD102")
            .provider(testProvider)
            .propertyId("HOTEL001")
            .guestName("User 2")
            .rating(3.0)
            .reviewText("Good stay")
            .reviewDate(LocalDateTime.now())
            .build();

    Review review3 =
        Review.builder()
            .externalId("AGD103")
            .provider(testProvider)
            .propertyId("HOTEL002")
            .guestName("User 3")
            .rating(4.0)
            .reviewText("Different hotel")
            .reviewDate(LocalDateTime.now())
            .build();

    reviewRepository.save(testReview);
    reviewRepository.save(review2);
    reviewRepository.save(review3);

    // When
    Long hotel1Count = reviewRepository.countReviewsByPropertyId("HOTEL001");
    Long hotel2Count = reviewRepository.countReviewsByPropertyId("HOTEL002");

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
              .externalId("AGD" + (100 + i))
              .provider(testProvider)
              .propertyId("HOTEL001")
              .guestName("User " + i)
              .rating(0.5 + i * 0.5)
              .reviewText("Review " + i)
              .reviewDate(LocalDateTime.now().minusDays(i))
              .build();
      reviewRepository.save(review);
    }

    // When - Sort by rating descending, page 1 (size 3)
    Pageable pageable = PageRequest.of(1, 3, Sort.by("rating").descending());
    Page<Review> reviewPage = reviewRepository.findByPropertyId("HOTEL001", pageable);

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
              invalidReview.setExternalId(""); // Blank external ID
              reviewRepository.saveAndFlush(invalidReview);
            })
        .isInstanceOf(ConstraintViolationException.class);

    assertThatThrownBy(
            () -> {
              Review invalidReview =
                  Review.builder()
                      .externalId("VALID_ID")
                      .provider(testProvider)
                      .propertyId("HOTEL001")
                      .guestName("Valid Name")
                      .rating(15.0) // Rating too high
                      .reviewText("Valid text")
                      .reviewDate(LocalDateTime.now())
                      .build();
              reviewRepository.saveAndFlush(invalidReview);
            })
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("Should handle unique constraint on external ID and provider")
  void shouldHandleUniqueConstraintOnExternalIdAndProvider() {
    // Given
    reviewRepository.save(testReview);

    // When & Then - Duplicate external ID with same provider should fail
    Review duplicateReview =
        Review.builder()
            .externalId("AGD123456") // Same external ID
            .provider(testProvider) // Same provider
            .propertyId("HOTEL002")
            .guestName("Another User")
            .rating(3.0)
            .reviewText("Different review text")
            .reviewDate(LocalDateTime.now())
            .build();

    assertThatThrownBy(() -> reviewRepository.saveAndFlush(duplicateReview))
        .hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class);
  }

  @Test
  @DisplayName("Should allow same external ID with different providers")
  void shouldAllowSameExternalIdWithDifferentProviders() {
    // Given
    Provider bookingProvider =
        Provider.builder().code("BOOKING").name("Booking.com").active(true).build();
    bookingProvider = entityManager.persistAndFlush(bookingProvider);

    reviewRepository.save(testReview);

    // When
    Review sameExternalIdDifferentProvider =
        Review.builder()
            .externalId("AGD123456") // Same external ID
            .provider(bookingProvider) // Different provider
            .propertyId("HOTEL002")
            .guestName("Another User")
            .rating(3.0)
            .reviewText("Different review text")
            .reviewDate(LocalDateTime.now())
            .build();

    Review savedReview = reviewRepository.save(sameExternalIdDifferentProvider);

    // Then
    assertThat(savedReview.getId()).isNotNull();
    assertThat(reviewRepository.count()).isEqualTo(2);
  }
}
