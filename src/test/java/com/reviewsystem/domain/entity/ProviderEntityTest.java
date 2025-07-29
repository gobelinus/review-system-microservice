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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DisplayName("Provider Entity Tests")
class ProviderEntityTest {

  private Validator validator;

  @Autowired private TestEntityManager entityManager;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Nested
  @DisplayName("Provider Validation")
  class ProviderValidation {

    @Test
    @DisplayName("Should create valid provider with all required fields")
    void shouldCreateValidProviderWithAllRequiredFields() {
      // Given
      Provider provider =
          Provider.builder()
              .name("Agoda")
              .code(ProviderType.AGODA)
              .apiEndpoint("https://api.agoda.com")
              .active(true)
              .build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when name is null")
    void shouldFailValidationWhenNameIsNull() {
      // Given
      Provider provider = Provider.builder().code(ProviderType.AGODA).active(true).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("provider name is required");
    }

    @Test
    @DisplayName("Should fail validation when name is blank")
    void shouldFailValidationWhenNameIsBlank() {
      // Given
      Provider provider =
          Provider.builder().name("   ").code(ProviderType.AGODA).active(true).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("provider name is required");
    }

    @Test
    @DisplayName("Should fail validation when apiEndpoint is invalid URL")
    void shouldFailValidationWhenApiEndpointIsInvalidUrl() {
      // Given
      Provider provider =
          Provider.builder()
              .name("Agoda")
              .code(ProviderType.AGODA)
              .apiEndpoint("invalid-url")
              .active(true)
              .build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("API endpoint must be a valid URL");
    }
  }

  @Nested
  @DisplayName("Provider-Specific Configurations")
  class platformSpecificConfigurations {

    @Test
    @DisplayName("Should handle Agoda-specific configuration")
    void shouldHandleAgodaSpecificConfiguration() {
      // Given
      Provider agodaplatform =
          Provider.builder()
              .name("Agoda")
              .code(ProviderType.AGODA)
              .active(true)
              .ratingScale(10.0)
              .supportedLanguages("en,th,zh,ja")
              .build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(agodaplatform);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should handle Booking.com-specific configuration")
    void shouldHandleBookingSpecificConfiguration() {
      // Given
      Provider bookingplatform =
          Provider.builder()
              .name("Booking.com")
              .code(ProviderType.BOOKING)
              .active(true)
              .ratingScale(5.0)
              .supportedLanguages("en,fr,de,es,it")
              .build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(bookingplatform);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when rating scale is invalid")
    void shouldFailValidationWhenRatingScaleIsInvalid() {
      // Given
      Provider platform =
          Provider.builder()
              .name("Agoda")
              .code(ProviderType.AGODA)
              .active(true)
              .ratingScale(0.0)
              .build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(platform);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Rating scale must be between 1.0 and 10.0");
    }
  }

  @Nested
  @DisplayName("platform Relationships")
  class platformRelationships {

    @Test
    @DisplayName("Should manage reviews collection correctly")
    void shouldManageReviewsCollectionCorrectly() {
      // Given
      Provider platform =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      Review review1 =
          Review.builder()
              .hotelId(456)
              .reviewerDisplayName("John Doe")
              .rating(4.5)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      Review review2 =
          Review.builder()
              .hotelId(457)
              .reviewerDisplayName("Jane Smith")
              .rating(3.5)
              .reviewComments("Good experience")
              .reviewDate(LocalDateTime.now().minusDays(2))
              .translateSource("en")
              .build();

      // When
      platform.addReview(review1);
      platform.addReview(review2);

      // Then
      assertThat(platform.getReviews()).hasSize(2);
      assertThat(platform.getReviews()).contains(review1, review2);
      assertThat(review1.getProvider()).isEqualTo(platform);
      assertThat(review2.getProvider()).isEqualTo(platform);
    }

    @Test
    @DisplayName("Should remove review correctly")
    void shouldRemoveReviewCorrectly() {
      // Given
      Provider platform =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      Review review =
          Review.builder()
              .hotelId(456)
              .reviewerDisplayName("John Doe")
              .rating(4.5)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      platform.addReview(review);

      // When
      platform.removeReview(review);

      // Then
      assertThat(platform.getReviews()).isEmpty();
      assertThat(review.getProvider()).isNull();
    }

    @Test
    @DisplayName("Should prevent duplicate reviews")
    void shouldPreventDuplicateReviews() {
      // Given
      Provider platform =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      Review review =
          Review.builder()
              .hotelId(456)
              .reviewerDisplayName("John Doe")
              .rating(4.5)
              .reviewComments("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .translateSource("en")
              .build();

      // When
      platform.addReview(review);
      platform.addReview(review); // Adding same review again

      // Then
      assertThat(platform.getReviews()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Business Logic Methods")
  class BusinessLogicMethods {

    @Test
    @DisplayName("Should check if platform is active")
    void shouldCheckIfplatformIsActive() {
      // Given
      Provider activeplatform =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      Provider inactiveplatform =
          Provider.builder().name("Expedia").code(ProviderType.EXPEDIA).active(false).build();

      // When & Then
      assertThat(activeplatform.isActive()).isTrue();
      assertThat(inactiveplatform.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should return zero average rating when no reviews")
    void shouldReturnZeroAverageRatingWhenNoReviews() {
      // Given
      Provider platform =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      // When
      double averageRating = platform.getAverageRating();

      // Then
      assertThat(averageRating).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should check if platform supports translateSource")
    void shouldCheckIfplatformSupportsLanguage() {
      // Given
      Provider platform =
          Provider.builder()
              .name("Agoda")
              .code(ProviderType.AGODA)
              .supportedLanguages("en,th,zh,ja")
              .active(true)
              .build();

      // When & Then
      assertThat(platform.supportsLanguage("en")).isTrue();
      assertThat(platform.supportsLanguage("th")).isTrue();
      assertThat(platform.supportsLanguage("fr")).isFalse();
      assertThat(platform.supportsLanguage("EN")).isTrue(); // Case insensitive
    }

    @Test
    @DisplayName("Should normalize rating to standard scale")
    void shouldNormalizeRatingToStandardScale() {
      // Given - Agoda uses 10-point scale
      Provider agodaplatform =
          Provider.builder()
              .name("Agoda")
              .code(ProviderType.AGODA)
              .ratingScale(10.0)
              .active(true)
              .build();

      // Given - Booking uses 5-point scale
      Provider bookingplatform =
          Provider.builder()
              .name("Booking")
              .code(ProviderType.BOOKING)
              .ratingScale(5.0)
              .active(true)
              .build();

      // When
      double normalizedAgodaRating = agodaplatform.normalizeRating(8.0); // 8/10 = 4.0/5
      double normalizedBookingRating = bookingplatform.normalizeRating(4.0); // 4/5 = 4.0/5

      // Then
      assertThat(normalizedAgodaRating).isEqualTo(4.0);
      assertThat(normalizedBookingRating).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Should activate and deactivate platform")
    void shouldActivateAndDeactivateplatform() {
      // Given
      Provider platform =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(false).build();

      // When
      platform.activate();

      // Then
      assertThat(platform.isActive()).isTrue();

      // When
      platform.deactivate();

      // Then
      assertThat(platform.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should update last processed timestamp")
    void shouldUpdateLastProcessedTimestamp() {
      // Given
      Provider platform =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      LocalDateTime beforeUpdate = LocalDateTime.now();

      // When
      platform.setUpdatedAt(beforeUpdate);

      // Then
      assertThat(platform.getUpdatedAt()).isEqualTo(beforeUpdate);
    }
  }

  @Nested
  @DisplayName("Equals, HashCode, and ToString")
  class EqualsHashCodeToString {

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
      // Given
      Provider platform1 =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      Provider platform2 =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      Provider platform3 =
          Provider.builder().name("Booking").code(ProviderType.BOOKING).active(true).build();

      // When & Then
      assertThat(platform1).isEqualTo(platform2);
      assertThat(platform1).isNotEqualTo(platform3);
      assertThat(platform1).isNotEqualTo(null);
      assertThat(platform1).isEqualTo(platform1);
    }

    @Test
    @DisplayName("Should implement hashCode consistently")
    void shouldImplementHashCodeConsistently() {
      // Given
      Provider platform1 =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      Provider platform2 =
          Provider.builder().name("Agoda").code(ProviderType.AGODA).active(true).build();

      // When & Then
      assertThat(platform1.hashCode()).isEqualTo(platform2.hashCode());
      assertThat(platform1.hashCode()).isEqualTo(platform1.hashCode());
    }

    @Test
    @DisplayName("Should implement toString with essential information")
    void shouldImplementToStringWithEssentialInformation() {
      // Given
      Provider provider =
          Provider.builder()
              .id(1L)
              .name("Agoda")
              .code(ProviderType.AGODA)
              .active(true)
              .ratingScale(10.0)
              .build();

      // When
      String toString = provider.toString();

      // Then
      assertThat(toString).contains("provider");
      assertThat(toString).contains("id=1");
      assertThat(toString).contains("name='Agoda'");
      assertThat(toString).contains("code='AGODA'");
      assertThat(toString).contains("active=true");
    }
  }
}
