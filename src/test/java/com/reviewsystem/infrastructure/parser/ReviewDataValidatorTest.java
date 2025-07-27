package com.reviewsystem.infrastructure.parser;

import static org.assertj.core.api.Assertions.*;

import com.reviewsystem.infrastructure.parser.dto.RawReviewData;
import com.reviewsystem.infrastructure.parser.dto.ValidationResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test-postgres")
class ReviewDataValidatorTest {

  private ReviewDataValidator validator;

  @BeforeEach
  void setUp() {
    validator = new ReviewDataValidator();
  }

  @Test
  void shouldValidateValidReviewData() {
    // Given
    RawReviewData validData = createValidRawReviewData();

    // When
    ValidationResult result = validator.validate(validData);

    // Then
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrors()).isEmpty();
  }

  @Test
  void shouldFailValidationForMissingHotelId() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setHotelId(null);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Hotel ID is required");
  }

  @Test
  void shouldFailValidationForInvalidHotelId() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setHotelId(-1);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Hotel ID must be positive");
  }

  @Test
  void shouldFailValidationForMissingProvider() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setProvider(null);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Provider is required");
  }

  @Test
  void shouldFailValidationForEmptyProvider() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setProvider("");

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Provider cannot be empty");
  }

  @Test
  void shouldFailValidationForInvalidProvider() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setProvider("InvalidProvider");

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Provider must be one of: Agoda, Booking, Expedia");
  }

  @Test
  void shouldFailValidationForMissingHotelName() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setHotelName(null);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Hotel name is required");
  }

  @Test
  void shouldFailValidationForTooLongHotelName() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setHotelName("A".repeat(256)); // Assuming max length is 255

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Hotel name cannot exceed 255 characters");
  }

  @Test
  void shouldFailValidationForMissingComment() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setComment(null);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Comment section is required");
  }

  @Test
  void shouldFailValidationForInvalidRating() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment().put("rating", -1.0);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Rating must be between 0 and 10");
  }

  @Test
  void shouldFailValidationForRatingTooHigh() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment().put("rating", 11.0);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Rating must be between 0 and 10");
  }

  @Test
  void shouldFailValidationForMissingReviewDate() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment().remove("reviewDate");

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Review date is required");
  }

  @Test
  void shouldFailValidationForInvalidDateFormat() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment().put("reviewDate", "invalid-date-format");

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Invalid review date format");
  }

  @Test
  void shouldFailValidationForFutureDate() {
    // Given
    RawReviewData data = createValidRawReviewData();
    LocalDateTime futureDate = LocalDateTime.now().plusDays(1);
    data.getComment().put("reviewDate", futureDate.toString());

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Review date cannot be in the future");
  }

  @Test
  void shouldFailValidationForTooOldDate() {
    // Given
    RawReviewData data = createValidRawReviewData();
    LocalDateTime oldDate = LocalDateTime.now().minusYears(21); // Assuming 20 years is the limit
    data.getComment().put("reviewDate", oldDate.toString());

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Review date cannot be older than 20 years");
  }

  @Test
  void shouldFailValidationForTooLongReviewComments() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment().put("reviewComments", "A".repeat(5001)); // Assuming max length is 5000

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Review comments cannot exceed 5000 characters");
  }

  @Test
  void shouldFailValidationForInvalidHotelReviewId() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment().put("hotelReviewId", -1);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Hotel review ID must be positive");
  }

  @Test
  void shouldAcceptValidBoundaryValues() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment().put("rating", 0.0); // Minimum rating
    data.getComment().put("reviewComments", "A".repeat(5000)); // Maximum length

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrors()).isEmpty();
  }

  @Test
  void shouldCollectMultipleValidationErrors() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.setHotelId(null);
    data.setProvider("");
    data.setHotelName(null);
    data.getComment().put("rating", -1.0);

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).hasSize(4);
    assertThat(result.getErrors())
        .contains(
            "Hotel ID is required",
            "Provider cannot be empty",
            "Hotel name is required",
            "Rating must be between 0 and 10");
  }

  @Test
  void shouldValidateOptionalFields() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment().remove("reviewPositives");
    data.getComment().remove("reviewNegatives");
    data.getComment().remove("reviewTitle");

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrors()).isEmpty();
  }

  @Test
  void shouldValidateReviewerInfoWhenPresent() {
    // Given
    RawReviewData data = createValidRawReviewData();
    data.getComment()
        .put(
            "reviewerInfo",
            new java.util.HashMap<String, Object>() {
              {
                put("countryName", "");
                put("lengthOfStay", -1);
              }
            });

    // When
    ValidationResult result = validator.validate(data);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors())
        .contains(
            "Country name cannot be empty when present",
            "Length of stay must be positive when present");
  }

  @Test
  void shouldHandleNullInput() {
    // Given
    RawReviewData nullData = null;

    // When
    ValidationResult result = validator.validate(nullData);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("Review data cannot be null");
  }

  private RawReviewData createValidRawReviewData() {
    return RawReviewData.builder()
        .hotelId(10984)
        .provider("Agoda")
        .hotelName("Oscar Saigon Hotel")
        .comment(
            new java.util.HashMap<String, Object>() {
              {
                put("hotelReviewId", 948353737);
                put("rating", 6.4);
                put("reviewComments", "Hotel room is basic and very small");
                put("reviewDate", "2025-04-10T05:37:00+07:00");
                put(
                    "reviewerInfo",
                    new java.util.HashMap<String, Object>() {
                      {
                        put("countryName", "India");
                        put("lengthOfStay", 2);
                      }
                    });
              }
            })
        .overallByproviders(new java.util.ArrayList<>())
        .lineNumber(1)
        .rawJson("{}")
        .build();
  }
}
