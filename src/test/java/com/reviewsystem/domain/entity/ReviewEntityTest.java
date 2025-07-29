package com.reviewsystem.domain.entity;

import static org.assertj.core.api.Assertions.*;

import com.reviewsystem.common.enums.ProviderType;
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
    provider = Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();
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
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .hotelName("Oscar Saigon Hotel")
              .reviewerDisplayName("John Doe")
              .rating(6.4)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when hotelReviewId is null")
    void shouldFailValidationWhenProviderReviewIdIsNull() {
      // Given
      Review review =
          Review.builder()
              .provider(provider)
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(6.4)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Provider review ID is required");
    }

    @Test
    @DisplayName("Should fail validation when hotelReviewId is blank")
    void shouldFailValidationWhenProviderReviewIdIsBlank() {
      // Given
      Review review =
          Review.builder()
              .hotelReviewId("   ")
              .provider(provider)
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(6.4)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Provider review ID is required");
    }

    @Test
    @DisplayName("Should fail validation when provider is null")
    void shouldFailValidationWhenProviderIsNull() {
      // Given
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(6.4)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("Provider is required");
    }

    @Test
    @DisplayName("Should fail validation when hotelId is null")
    void shouldFailValidationWhenHotelIdIsNull() {
      // Given
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .reviewerDisplayName("John Doe")
              .rating(6.4)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("Hotel ID is required");
    }

    @Test
    @DisplayName("Should fail validation when rating is out of range")
    void shouldFailValidationWhenRatingIsOutOfRange() {
      // Given - Rating above maximum (10.0 scale)
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(11.0)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Rating must not exceed 10.0");
    }

    @Test
    @DisplayName("Should fail validation when rating is negative")
    void shouldFailValidationWhenRatingIsNegative() {
      // Given
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(-1.0)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Rating must be non-negative");
    }

    @Test
    @DisplayName("Should fail validation when reviewDate is in the future")
    void shouldFailValidationWhenReviewDateIsInFuture() {
      // Given
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(6.4)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().plusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Review date cannot be in the future");
    }

    @Test
    @DisplayName("Should accept valid 10-point scale ratings")
    void shouldAcceptValid10PointScaleRatings() {
      // Given
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(8.5)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).isEmpty();
    }
  }

  @Nested
  @DisplayName("Entity Relationships")
  class EntityRelationships {

    @Test
    @DisplayName("Should maintain bidirectional relationship with provider")
    void shouldMaintainBidirectionalRelationshipWithProvider() {
      // Given
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(6.4)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
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
          Provider.builder().name("Booking").code(ProviderType.BOOKING).active(true).build();

      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .reviewerDisplayName("John Doe")
              .rating(6.4)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
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
    @DisplayName("Should determine if review is positive (10-point scale)")
    void shouldDetermineIfReviewIsPositive() {
      // Given
      Review positiveReview = Review.builder().rating(7.0).build();
      Review highPositiveReview = Review.builder().rating(9.5).build();
      Review neutralReview = Review.builder().rating(6.0).build();
      Review negativeReview = Review.builder().rating(4.0).build();

      // When & Then
      assertThat(positiveReview.isPositive()).isTrue();
      assertThat(highPositiveReview.isPositive()).isTrue();
      assertThat(neutralReview.isPositive()).isFalse();
      assertThat(negativeReview.isPositive()).isFalse();
    }

    @Test
    @DisplayName("Should determine if review is negative (10-point scale)")
    void shouldDetermineIfReviewIsNegative() {
      // Given
      Review positiveReview = Review.builder().rating(7.0).build();
      Review neutralReview = Review.builder().rating(6.0).build();
      Review negativeReview = Review.builder().rating(4.0).build();
      Review veryNegativeReview = Review.builder().rating(2.0).build();

      // When & Then
      assertThat(positiveReview.isNegative()).isFalse();
      assertThat(neutralReview.isNegative()).isFalse();
      assertThat(negativeReview.isNegative()).isTrue();
      assertThat(veryNegativeReview.isNegative()).isTrue();
    }

    @Test
    @DisplayName("Should determine if review is neutral (10-point scale)")
    void shouldDetermineIfReviewIsNeutral() {
      // Given
      Review positiveReview = Review.builder().rating(7.5).build();
      Review neutralReview1 = Review.builder().rating(5.0).build();
      Review neutralReview2 = Review.builder().rating(6.5).build();
      Review negativeReview = Review.builder().rating(4.5).build();

      // When & Then
      assertThat(positiveReview.isNeutral()).isFalse();
      assertThat(neutralReview1.isNeutral()).isTrue();
      assertThat(neutralReview2.isNeutral()).isTrue();
      assertThat(negativeReview.isNeutral()).isFalse();
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
      Review reviewWithText = Review.builder().reviewComments("Great experience!").build();
      Review reviewWithoutText = Review.builder().reviewComments("").build();
      Review reviewWithNullText = Review.builder().reviewComments(null).build();

      // When & Then
      assertThat(reviewWithText.hasTextContent()).isTrue();
      assertThat(reviewWithoutText.hasTextContent()).isFalse();
      assertThat(reviewWithNullText.hasTextContent()).isFalse();
    }

    @Test
    @DisplayName("Should check if review has title")
    void shouldCheckIfReviewHasTitle() {
      // Given
      Review reviewWithTitle = Review.builder().reviewTitle("Perfect location").build();
      Review reviewWithoutTitle = Review.builder().reviewTitle("").build();
      Review reviewWithNullTitle = Review.builder().reviewTitle(null).build();

      // When & Then
      assertThat(reviewWithTitle.hasTitle()).isTrue();
      assertThat(reviewWithoutTitle.hasTitle()).isFalse();
      assertThat(reviewWithNullTitle.hasTitle()).isFalse();
    }

    @Test
    @DisplayName("Should generate unique business key")
    void shouldGenerateUniqueBusinessKey() {
      // Given
      Review review1 = Review.builder().hotelReviewId("948353737").provider(provider).build();
      Review review2 = Review.builder().hotelReviewId("948353738").provider(provider).build();
      Review review3 = Review.builder().hotelReviewId("948353737").provider(provider).build();

      // When
      String key1 = review1.getBusinessKey();
      String key2 = review2.getBusinessKey();
      String key3 = review3.getBusinessKey();

      // Then
      assertThat(key1).isNotEqualTo(key2);
      assertThat(key1).isEqualTo(key3);
      assertThat(key1).contains("Agoda");
      assertThat(key1).contains("948353737");
    }

    @Test
    @DisplayName("Should check if reviewer is solo traveler")
    void shouldCheckIfReviewerIsSoloTraveler() {
      // Given
      Review soloReview = Review.builder().reviewGroup("Solo traveler").build();
      Review familyReview = Review.builder().reviewGroup("Family").build();
      Review nullGroupReview = Review.builder().reviewGroup(null).build();

      // When & Then
      assertThat(soloReview.isSoloTraveler()).isTrue();
      assertThat(familyReview.isSoloTraveler()).isFalse();
      assertThat(nullGroupReview.isSoloTraveler()).isFalse();
    }

    @Test
    @DisplayName("Should check if reviewer is experienced")
    void shouldCheckIfReviewerIsExperienced() {
      // Given
      Review experiencedReview = Review.builder().reviewerReviewCount(10).build();
      Review newReview = Review.builder().reviewerReviewCount(3).build();
      Review nullCountReview = Review.builder().reviewerReviewCount(null).build();

      // When & Then
      assertThat(experiencedReview.isExperiencedReviewer()).isTrue();
      assertThat(newReview.isExperiencedReviewer()).isFalse();
      assertThat(nullCountReview.isExperiencedReviewer()).isFalse();
    }

    @Test
    @DisplayName("Should mark review processing status correctly")
    void shouldMarkReviewProcessingStatusCorrectly() {
      // Given
      Review review = Review.builder().build();

      // When & Then - Initial state
      assertThat(review.getProcessingStatus()).isEqualTo(Review.ProcessingStatus.PENDING);
      assertThat(review.isProcessed()).isFalse();

      // When & Then - Mark as processed
      review.markAsProcessed();
      assertThat(review.getProcessingStatus()).isEqualTo(Review.ProcessingStatus.PROCESSED);
      assertThat(review.isProcessed()).isTrue();

      // When & Then - Mark as failed
      review.markAsFailed();
      assertThat(review.getProcessingStatus()).isEqualTo(Review.ProcessingStatus.FAILED);
      assertThat(review.isProcessed()).isFalse();
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
          Review.builder().hotelReviewId("948353737").provider(provider).hotelId(10984).build();

      Review review2 =
          Review.builder().hotelReviewId("948353737").provider(provider).hotelId(10984).build();

      Review review3 =
          Review.builder().hotelReviewId("948353738").provider(provider).hotelId(10984).build();

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
          Review.builder().hotelReviewId("948353737").provider(provider).hotelId(10984).build();

      Review review2 =
          Review.builder().hotelReviewId("948353737").provider(provider).hotelId(10984).build();

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
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .hotelName("Oscar Saigon Hotel")
              .rating(6.4)
              .translateSource("en")
              .processingStatus(Review.ProcessingStatus.PROCESSED)
              .build();

      // When
      String toString = review.toString();

      // Then
      assertThat(toString).contains("Review");
      assertThat(toString).contains("id=1");
      assertThat(toString).contains("hotelReviewId='948353737'");
      assertThat(toString).contains("hotelId=10984");
      assertThat(toString).contains("hotelName='Oscar Saigon Hotel'");
      assertThat(toString).contains("rating=6.4");
      assertThat(toString).contains("translateSource='en'");
      assertThat(toString).contains("processingStatus=PROCESSED");
    }
  }

  @Nested
  @DisplayName("New Field Validations")
  class NewFieldValidations {

    @Test
    @DisplayName("Should validate hotel name length")
    void shouldValidateHotelNameLength() {
      // Given - Hotel name exceeding 300 characters
      String longHotelName = "a".repeat(301);
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .hotelName(longHotelName)
              .rating(6.4)
              .reviewDate(LocalDateTime.now().minusDays(1))
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Hotel name must not exceed 300 characters");
    }

    @Test
    @DisplayName("Should validate review title length")
    void shouldValidateReviewTitleLength() {
      // Given - Review title exceeding 500 characters
      String longTitle = "a".repeat(501);
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .reviewTitle(longTitle)
              .rating(6.4)
              .reviewDate(LocalDateTime.now().minusDays(1))
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Review title must not exceed 500 characters");
    }

    @Test
    @DisplayName("Should validate length of stay is non-negative")
    void shouldValidateLengthOfStayIsNonNegative() {
      // Given - Negative length of stay
      Review review =
          Review.builder()
              .hotelReviewId("948353737")
              .provider(provider)
              .hotelId(10984)
              .lengthOfStay(-1)
              .rating(6.4)
              .reviewDate(LocalDateTime.now().minusDays(1))
              .build();

      // When
      Set<ConstraintViolation<Review>> violations = validator.validate(review);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Length of stay cannot be negative");
    }
  }
}
