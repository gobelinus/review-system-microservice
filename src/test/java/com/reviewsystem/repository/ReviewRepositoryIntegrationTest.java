package com.reviewsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reviewsystem.ReviewSystemMicroserviceApplication;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.Provider;
import com.reviewsystem.domain.entity.Review;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = ReviewSystemMicroserviceApplication.class)
@Testcontainers
@ActiveProfiles("test-postgres")
@DisplayName("ReviewRepository Integration Tests")
class ReviewRepositoryIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("review_system_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private ReviewRepository reviewRepository;

  @Autowired private ProviderRepository providerRepository;

  @PersistenceContext private EntityManager entityManager;

  private Provider testProvider;

  @BeforeEach
  void setUp() {
    try {
      reviewRepository.deleteAll();
    } catch (Exception e) {
      // pass
    }
    try {
      providerRepository.deleteAll();
    } catch (Exception e) {
      // pass
    }
    testProvider =
        Provider.builder()
            .code(ProviderType.AGODA)
            .name("Agoda.com")
            .description("Agoda travel provider")
            .active(true)
            .build();
    testProvider = providerRepository.save(testProvider);
  }

  private double getRandomRating() {
    int ratingMinInt = (int) (1.0 * 10);
    int ratingMaxInt = (int) (5.0 * 10);
    return (ThreadLocalRandom.current().nextInt(ratingMinInt, ratingMaxInt + 1) / 10.0);
  }

  @Test
  @DisplayName("Should handle large dataset operations efficiently")
  void shouldHandleLargeDatasetOperationsEfficiently() {
    // Given - Create 1000 reviews
    for (int i = 0; i < 1000; i++) {
      Review review =
          Review.builder()
              .provider(testProvider)
              .hotelId((i % 10) + 1)
              .reviewerDisplayName("User " + i)
              .rating(getRandomRating())
              .reviewComments("Review text " + i)
              .reviewDate(LocalDateTime.now().minusDays(i % 30))
              .translateSource(i % 2 == 0 ? "en" : "fr")
              .build();
      reviewRepository.save(review);
    }

    // When
    long startTime = System.currentTimeMillis();
    List<Review> highRatedReviews = reviewRepository.findByRatingGreaterThanEqual(4.0);
    long endTime = System.currentTimeMillis();

    // Then
    assertThat(highRatedReviews).hasSizeGreaterThan(100);
    assertThat(endTime - startTime).isLessThan(1000); // Should complete within 1 second

    // Verify database contains all reviews
    assertThat(reviewRepository.count()).isEqualTo(1000);
  }

  @Test
  @DisplayName("Should maintain referential integrity with providers")
  void shouldMaintainReferentialIntegrityWithproviders() {
    // Given
    Review review =
        Review.builder()
            .provider(testProvider)
            .hotelId(1)
            .reviewerDisplayName("Test User")
            .rating(getRandomRating())
            .reviewComments("Test review")
            .reviewDate(LocalDateTime.now())
            .build();
    reviewRepository.save(review);

    // When & Then - Should not be able to delete provider with associated reviews
    assertThatThrownBy(
            () -> {
              providerRepository.delete(testProvider);
              entityManager.flush();
            })
        .satisfiesAnyOf(
            throwable ->
                assertThat(throwable)
                    .hasRootCauseInstanceOf(
                        org.hibernate.exception.ConstraintViolationException.class),
            throwable ->
                assertThat(throwable)
                    .hasRootCauseInstanceOf(org.hibernate.TransientObjectException.class));

    // Verify provider and review still exist
    assertThat(providerRepository.count()).isEqualTo(1);
    assertThat(reviewRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should handle complex query with joins efficiently")
  void shouldHandleComplexQueryWithJoinsEfficiently() {
    // Given - Create reviews with different providers
    Provider bookingprovider =
        providerRepository.save(
            Provider.builder().code(ProviderType.BOOKING).name("Booking.com").active(true).build());
    Provider expediaprovider =
        providerRepository.save(
            Provider.builder().code(ProviderType.EXPEDIA).name("Expedia.com").active(true).build());

    for (int i = 0; i < 100; i++) {
      Provider provider =
          switch (i % 3) {
            case 0 -> testProvider;
            case 1 -> bookingprovider;
            case 2 -> expediaprovider;
            default -> testProvider;
          };

      Review review =
          Review.builder()
              .provider(provider)
              .hotelId((i % 5) + 1)
              .reviewerDisplayName("User " + i)
              .rating(getRandomRating())
              .reviewComments("Review " + i)
              .reviewDate(LocalDateTime.now().minusDays(i % 10))
              .build();
      reviewRepository.save(review);
    }

    // When
    long startTime = System.currentTimeMillis();
    List<Review> recentAgodaReviews =
        reviewRepository.findRecentReviewsByProvider(
            testProvider, LocalDateTime.now().minusDays(5));
    long endTime = System.currentTimeMillis();

    // Then
    assertThat(recentAgodaReviews).hasSizeGreaterThan(10);
    assertThat(endTime - startTime).isLessThan(500); // Should be fast due to indexing

    // Verify all reviews are from correct provider and within date range
    recentAgodaReviews.forEach(
        review -> {
          assertThat(review.getProvider()).isEqualTo(testProvider);
          assertThat(review.getReviewDate()).isAfter(LocalDateTime.now().minusDays(5));
        });
  }

  @Test
  @DisplayName("Should handle batch operations efficiently")
  void shouldHandleBatchOperationsEfficiently() {
    // Given
    List<Review> reviews = new java.util.ArrayList<>();
    for (int i = 0; i < 500; i++) {
      Review review =
          Review.builder()
              .provider(testProvider)
              .hotelId(1)
              .reviewerDisplayName("User " + i)
              .rating(getRandomRating())
              .reviewComments("Batch review " + i)
              .reviewDate(LocalDateTime.now().minusMinutes(i))
              .build();
      reviews.add(review);
    }

    // When
    long startTime = System.currentTimeMillis();
    List<Review> savedReviews = reviewRepository.saveAll(reviews);
    long endTime = System.currentTimeMillis();

    // Then
    assertThat(savedReviews).hasSize(500);
    assertThat(endTime - startTime).isLessThan(5000); // Should complete within 5 seconds
    assertThat(reviewRepository.count()).isEqualTo(500);

    // Verify batch delete
    startTime = System.currentTimeMillis();
    reviewRepository.deleteAll(savedReviews);
    endTime = System.currentTimeMillis();

    assertThat(endTime - startTime).isLessThan(2000); // Delete should be fast
    assertThat(reviewRepository.count()).isEqualTo(0);
  }
}
