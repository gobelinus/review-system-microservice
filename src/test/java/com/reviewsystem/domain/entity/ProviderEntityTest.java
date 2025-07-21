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

@DisplayName("Provider Entity Tests")
class ProviderEntityTest {

  private Validator validator;

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
              .code("AGODA")
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
      Provider provider = Provider.builder().code("AGODA").active(true).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("Provider name is required");
    }

    @Test
    @DisplayName("Should fail validation when name is blank")
    void shouldFailValidationWhenNameIsBlank() {
      // Given
      Provider provider = Provider.builder().name("   ").code("AGODA").active(true).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("Provider name is required");
    }

    @Test
    @DisplayName("Should fail validation when code is null")
    void shouldFailValidationWhenCodeIsNull() {
      // Given
      Provider provider = Provider.builder().name("Agoda").active(true).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("Provider code is required");
    }

    @Test
    @DisplayName("Should fail validation when code is not uppercase")
    void shouldFailValidationWhenCodeIsNotUppercase() {
      // Given
      Provider provider = Provider.builder().name("Agoda").code("agoda").active(true).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Provider code must be uppercase letters only");
    }

    @Test
    @DisplayName("Should fail validation when code contains special characters")
    void shouldFailValidationWhenCodeContainsSpecialCharacters() {
      // Given
      Provider provider = Provider.builder().name("Agoda").code("AGODA-123").active(true).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Provider code must be uppercase letters only");
    }

    @Test
    @DisplayName("Should fail validation when code is too long")
    void shouldFailValidationWhenCodeIsTooLong() {
      // Given
      Provider provider =
          Provider.builder().name("Agoda").code("VERYLONGPROVIDERCODE").active(true).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Provider code must be between 2 and 10 characters");
    }

    @Test
    @DisplayName("Should fail validation when apiEndpoint is invalid URL")
    void shouldFailValidationWhenApiEndpointIsInvalidUrl() {
      // Given
      Provider provider =
          Provider.builder()
              .name("Agoda")
              .code("AGODA")
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
  class ProviderSpecificConfigurations {

    @Test
    @DisplayName("Should handle Agoda-specific configuration")
    void shouldHandleAgodaSpecificConfiguration() {
      // Given
      Provider agodaProvider =
          Provider.builder()
              .name("Agoda")
              .code("AGODA")
              .active(true)
              .ratingScale(10.0)
              .supportedLanguages("en,th,zh,ja")
              .processingPriority(1)
              .build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(agodaProvider);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should handle Booking.com-specific configuration")
    void shouldHandleBookingSpecificConfiguration() {
      // Given
      Provider bookingProvider =
          Provider.builder()
              .name("Booking.com")
              .code("BOOKING")
              .active(true)
              .ratingScale(5.0)
              .supportedLanguages("en,fr,de,es,it")
              .processingPriority(2)
              .build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(bookingProvider);

      // Then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when rating scale is invalid")
    void shouldFailValidationWhenRatingScaleIsInvalid() {
      // Given
      Provider provider =
          Provider.builder().name("Agoda").code("AGODA").active(true).ratingScale(0.0).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Rating scale must be between 1.0 and 10.0");
    }

    @Test
    @DisplayName("Should fail validation when processing priority is invalid")
    void shouldFailValidationWhenProcessingPriorityIsInvalid() {
      // Given
      Provider provider =
          Provider.builder().name("Agoda").code("AGODA").active(true).processingPriority(0).build();

      // When
      Set<ConstraintViolation<Provider>> violations = validator.validate(provider);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Processing priority must be between 1 and 10");
    }
  }

  @Nested
  @DisplayName("Provider Relationships")
  class ProviderRelationships {

    @Test
    @DisplayName("Should manage reviews collection correctly")
    void shouldManageReviewsCollectionCorrectly() {
      // Given
      Provider provider = Provider.builder().name("Agoda").code("AGODA").active(true).build();

      Review review1 =
          Review.builder()
              .externalId("ext-123")
              .propertyId("prop-456")
              .guestName("John Doe")
              .rating(4.5)
              .reviewText("Great hotel!")
              .reviewDate(LocalDateTime.now().minusDays(1))
              .language("en")
              .build();

      Review review2 =
          Review.builder()
              .externalId("ext-124")
              .propertyId("prop-457")
              .guestName("Jane Smith")
              .rating(3.5)
              .reviewText("Good experience")
              .reviewDate(LocalDateTime.now().minusDays(2))
              .language("en")
              .build();

      // When
      provider.addReview(review1);
      provider.addReview(review2);

      // Then
      assertThat(provider.getReviews()).hasSize(2);
      assertThat(provider.getReviews()).contains(review1, review2);
      assertThat(review1.getProvider()).isEqualTo(provider);
      assertThat(review2.getProvider()).isEqualTo(provider);
    }

    @Test
    @DisplayName("Should remove review correctly")
    void shouldRemoveReviewCorrectly() {
      // Given
      Provider provider = Provider.builder().name("Agoda").code("AGODA").active(true).build();

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

      provider.addReview(review);

      // When
      provider.removeReview(review);

      // Then
      assertThat(provider.getReviews()).isEmpty();
      assertThat(review.getProvider()).isNull();
    }

    @Test
    @DisplayName("Should prevent duplicate reviews")
    void shouldPreventDuplicateReviews() {
      // Given
      Provider provider = Provider.builder().name("Agoda").code("AGODA").active(true).build();

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
      provider.addReview(review);
      provider.addReview(review); // Adding same review again

      // Then
      assertThat(provider.getReviews()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Business Logic Methods")
  class BusinessLogicMethods {

    @Test
    @DisplayName("Should check if provider is active")
    void shouldCheckIfProviderIsActive() {
      // Given
      Provider activeProvider = Provider.builder().name("Agoda").code("AGODA").active(true).build();

      Provider inactiveProvider =
          Provider.builder().name("Expedia").code("EXPEDIA").active(false).build();

      // When & Then
      assertThat(activeProvider.isActive()).isTrue();
      assertThat(inactiveProvider.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should return zero average rating when no reviews")
    void shouldReturnZeroAverageRatingWhenNoReviews() {
      // Given
      Provider provider = Provider.builder().name("Agoda").code("AGODA").active(true).build();

      // When
      double averageRating = provider.getAverageRating();

      // Then
      assertThat(averageRating).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should check if provider supports language")
    void shouldCheckIfProviderSupportsLanguage() {
      // Given
      Provider provider =
          Provider.builder()
              .name("Agoda")
              .code("AGODA")
              .supportedLanguages("en,th,zh,ja")
              .active(true)
              .build();

      // When & Then
      assertThat(provider.supportsLanguage("en")).isTrue();
      assertThat(provider.supportsLanguage("th")).isTrue();
      assertThat(provider.supportsLanguage("fr")).isFalse();
      assertThat(provider.supportsLanguage("EN")).isTrue(); // Case insensitive
    }

    @Test
    @DisplayName("Should normalize rating to standard scale")
    void shouldNormalizeRatingToStandardScale() {
      // Given - Agoda uses 10-point scale
      Provider agodaProvider =
          Provider.builder().name("Agoda").code("AGODA").ratingScale(10.0).active(true).build();

      // Given - Booking uses 5-point scale
      Provider bookingProvider =
          Provider.builder().name("Booking").code("BOOKING").ratingScale(5.0).active(true).build();

      // When
      double normalizedAgodaRating = agodaProvider.normalizeRating(8.0); // 8/10 = 4.0/5
      double normalizedBookingRating = bookingProvider.normalizeRating(4.0); // 4/5 = 4.0/5

      // Then
      assertThat(normalizedAgodaRating).isEqualTo(4.0);
      assertThat(normalizedBookingRating).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Should activate and deactivate provider")
    void shouldActivateAndDeactivateProvider() {
      // Given
      Provider provider = Provider.builder().name("Agoda").code("AGODA").active(false).build();

      // When
      provider.activate();

      // Then
      assertThat(provider.isActive()).isTrue();

      // When
      provider.deactivate();

      // Then
      assertThat(provider.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should update last processed timestamp")
    void shouldUpdateLastProcessedTimestamp() {
      // Given
      Provider provider = Provider.builder().name("Agoda").code("AGODA").active(true).build();

      LocalDateTime beforeUpdate = LocalDateTime.now();

      // When
      provider.updateLastProcessedTimestamp();

      // Then
      assertThat(provider.getLastProcessedAt()).isAfter(beforeUpdate);
    }
  }

  @Nested
  @DisplayName("Equals, HashCode, and ToString")
  class EqualsHashCodeToString {

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
      // Given
      Provider provider1 = Provider.builder().name("Agoda").code("AGODA").active(true).build();

      Provider provider2 = Provider.builder().name("Agoda").code("AGODA").active(true).build();

      Provider provider3 = Provider.builder().name("Booking").code("BOOKING").active(true).build();

      // When & Then
      assertThat(provider1).isEqualTo(provider2);
      assertThat(provider1).isNotEqualTo(provider3);
      assertThat(provider1).isNotEqualTo(null);
      assertThat(provider1).isEqualTo(provider1);
    }

    @Test
    @DisplayName("Should implement hashCode consistently")
    void shouldImplementHashCodeConsistently() {
      // Given
      Provider provider1 = Provider.builder().name("Agoda").code("AGODA").active(true).build();

      Provider provider2 = Provider.builder().name("Agoda").code("AGODA").active(true).build();

      // When & Then
      assertThat(provider1.hashCode()).isEqualTo(provider2.hashCode());
      assertThat(provider1.hashCode()).isEqualTo(provider1.hashCode());
    }

    @Test
    @DisplayName("Should implement toString with essential information")
    void shouldImplementToStringWithEssentialInformation() {
      // Given
      Provider provider =
          Provider.builder()
              .id(1L)
              .name("Agoda")
              .code("AGODA")
              .active(true)
              .ratingScale(10.0)
              .build();

      // When
      String toString = provider.toString();

      // Then
      assertThat(toString).contains("Provider");
      assertThat(toString).contains("id=1");
      assertThat(toString).contains("name='Agoda'");
      assertThat(toString).contains("code='AGODA'");
      assertThat(toString).contains("active=true");
    }
  }
}
