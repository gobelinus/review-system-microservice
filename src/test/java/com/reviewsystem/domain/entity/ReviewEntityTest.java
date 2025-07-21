package com.reviewsystem.domain.entity;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Review Entity Tests")
class ReviewEntityTest {

  private Validator validator;
  private Provider provider;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();

    // Create a valid provider for testing
    provider = Provider.builder().name("Agoda").code("AGODA").active(true).build();
  }

  @Nested
  @DisplayName("Entity Validation Rules")
  class ValidationRules {

    @Test
    @DisplayName("Should create valid review with all required fields")
    void shouldCreateValidReviewWithAllRequiredFields() {
      // Given
      Review review =
          Review.builder()
              .externalId("ext-123")
              .provider(provider)
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when externalId is null")
    void shouldFailValidationWhenExternalIdIsNull() {
      // Given
      Review review =
          Review.builder()
              .provider(provider)
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("External ID is required");
    }

    @Test
    @DisplayName("Should fail validation when externalId is blank")
    void shouldFailValidationWhenExternalIdIsBlank() {
      // Given
      Review review =
          Review.builder()
              .externalId("   ")
              .provider(provider)
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("External ID is required");
    }

    @Test
    @DisplayName("Should fail validation when provider is null")
    void shouldFailValidationWhenProviderIsNull() {
      // Given
      Review review =
          Review.builder()
              .externalId("ext-123")
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("Provider is required");
    }

    @Test
    @DisplayName("Should fail validation when propertyId is null")
    void shouldFailValidationWhenPropertyIdIsNull() {
      // Given
      Review review =
          Review.builder()
              .externalId("ext-123")
              .provider(provider)
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("Property ID is required");
    }

    @Test
    @DisplayName("Should fail validation when rating is out of range")
    void shouldFailValidationWhenRatingIsOutOfRange() {
      // Given - Rating above maximum
      Review review =
          Review.builder()
              .externalId("ext-123")
              .provider(provider)
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(6.0)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Rating must be between 0.0 and 5.0");
    }

    @Test
    @DisplayName("Should fail validation when rating is negative")
    void shouldFailValidationWhenRatingIsNegative() {
      // Given
      Review review =
          Review.builder()
              .externalId("ext-123")
              .provider(provider)
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(-1.0)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Rating must be between 0.0 and 5.0");
    }

    @Test
    @DisplayName("Should fail validation when reviewDate is in the future")
    void shouldFailValidationWhenReviewDateIsInFuture() {
      // Given
      Review review =
          Review.builder()
              .externalId("ext-123")
              .provider(provider)
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().plusDays(1))
              .language("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Review date cannot be in the future");
    }
  }

  @Nested
  @DisplayName("Entity Relationships")
  class EntityRelationships {

    @Test
    @DisplayName("Should maintain bidirectional relationship with Provider")
    void shouldMaintainBidirectionalRelationshipWithProvider() {
      // Given
      Review review =
          Review.builder()
              .externalId("ext-123")
              .provider(provider)
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      // When
      provider.addReview(review);

      // Then
      assertThat(review.getProvider()).isEqualTo(provider);
      assertThat(provider.getReviews()).contains(review);
    }

    @Test
    @DisplayName("Should handle provider change correctly")
    void shouldHandleProviderChangeCorrectly() {
      // Given
      Provider newProvider =
          Provider.builder().name("Booking").code("BOOKING").active(true).build();

      Review review =
          Review.builder()
              .externalId("ext-123")
              .provider(provider)
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      provider.addReview(review);

      // When
      review.setProvider(newProvider);
      newProvider.addReview(review);

      // Then
      assertThat(review.getProvider()).isEqualTo(newProvider);
      assertThat(newProvider.getReviews()).contains(review);
      assertThat(provider.getReviews()).doesNotContain(review);
    }
  }

  @Nested
  @DisplayName("Business Logic Methods")
  class BusinessLogicMethods {

    @Test
    @DisplayName("Should determine if review is positive")
    void shouldDetermineIfReviewIsPositive() {
      // Given
      Review positiveReview = Review.builder().rating(4.0).build();

      Review neutralReview = Review.builder().rating(3.0).build();

      Review negativeReview = Review.builder().rating(2.0).build();

      // When & Then
      assertThat(positiveReview.isPositive()).isTrue();
      assertThat(neutralReview.isPositive()).isFalse();
      assertThat(negativeReview.isPositive()).isFalse();
    }

    @Test
    @DisplayName("Should determine if review is negative")
    void shouldDetermineIfReviewIsNegative() {
      // Given
      Review positiveReview = Review.builder().rating(4.0).build();

      Review neutralReview = Review.builder().rating(3.0).build();

      Review negativeReview = Review.builder().rating(2.0).build();

      // When & Then
      assertThat(positiveReview.isNegative()).isFalse();
      assertThat(neutralReview.isNegative()).isFalse();
      assertThat(negativeReview.isNegative()).isTrue();
    }

    @Test
    @DisplayName("Should calculate review age in days")
    void shouldCalculateReviewAgeInDays() {
      // Given
      LocalDateTime fiveDaysAgo = LocalDateTime.now().minusDays(5);
      Review review = Review.builder().reviewDate(fiveDaysAgo).build();

      // When
      long ageInDays = review.getAgeInDays();

      // Then
      assertThat(ageInDays).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should check if review is recent")
    void shouldCheckIfReviewIsRecent() {
      // Given
      Review recentReview = Review.builder().reviewDate(LocalDateTime.now().minusDays(15)).build();

      Review oldReview = Review.builder().reviewDate(LocalDateTime.now().minusDays(45)).build();

      // When & Then
      assertThat(recentReview.isRecent()).isTrue();
      assertThat(oldReview.isRecent()).isFalse();
    }

    @Test
    @DisplayName("Should check if review has text content")
    void shouldCheckIfReviewHasTextContent() {
      // Given
      Review reviewWithText = Review.builder().reviewText("Great experience!").build();

      Review reviewWithoutText = Review.builder().reviewText("").build();

      Review reviewWithNullText = Review.builder().reviewText(null).build();

      // When & Then
      assertThat(reviewWithText.hasTextContent()).isTrue();
      assertThat(reviewWithoutText.hasTextContent()).isFalse();
      assertThat(reviewWithNullText.hasTextContent()).isFalse();
    }

    @Test
    @DisplayName("Should generate unique business key")
    void shouldGenerateUniqueBusinessKey() {
      // Given
      Review review1 = Review.builder().externalId("ext-123").provider(provider).build();

      Review review2 = Review.builder().externalId("ext-456").provider(provider).build();

      Review review3 = Review.builder().externalId("ext-123").provider(provider).build();

      // When
      String key1 = review1.getBusinessKey();
      String key2 = review2.getBusinessKey();
      String key3 = review3.getBusinessKey();

      // Then
      assertThat(key1).isNotEqualTo(key2);
      assertThat(key1).isEqualTo(key3);
      assertThat(key1).contains("AGODA");
      assertThat(key1).contains("ext-123");
    }
  }

  @Nested
  @DisplayName("Equals, HashCode, and ToString")
  class EqualsHashCodeToString {

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
      // Given
      Review review1 =
          Review.builder().externalId("ext-123").provider(provider).propertyId("prop-456").build();

      Review review2 =
          Review.builder().externalId("ext-123").provider(provider).propertyId("prop-456").build();

      Review review3 =
          Review.builder().externalId("ext-456").provider(provider).propertyId("prop-456").build();

      // When & Then
      assertThat(review1).isEqualTo(review2);
      assertThat(review1).isNotEqualTo(review3);
      assertThat(review1).isNotEqualTo(null);
      assertThat(review1).isEqualTo(review1);
    }

    @Test
    @DisplayName("Should implement hashCode consistently")
    void shouldImplementHashCodeConsistently() {
      // Given
      Review review1 =
          Review.builder().externalId("ext-123").provider(provider).propertyId("prop-456").build();

      Review review2 =
          Review.builder().externalId("ext-123").provider(provider).propertyId("prop-456").build();

      // When & Then
      assertThat(review1.hashCode()).isEqualTo(review2.hashCode());
      assertThat(review1.hashCode()).isEqualTo(review1.hashCode());
    }

    @Test
    @DisplayName("Should implement toString with essential information")
    void shouldImplementToStringWithEssentialInformation() {
      // Given
      Review review =
          Review.builder()
              .id(1L)
              .externalId("ext-123")
              .provider(provider)
              .propertyId("prop-456")
              .rating(4.5)
              .build();

      // When
      String toString = review.toString();

      // Then
      assertThat(toString).contains("Review");
      assertThat(toString).contains("id=1");
      assertThat(toString).contains("externalId='ext-123'");
      assertThat(toString).contains("rating=4.5");
    }
  }
}
