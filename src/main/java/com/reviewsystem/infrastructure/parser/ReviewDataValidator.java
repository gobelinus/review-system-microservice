package com.reviewsystem.infrastructure.parser;

import com.reviewsystem.infrastructure.parser.dto.RawReviewData;
import com.reviewsystem.infrastructure.parser.dto.ValidationResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReviewDataValidator {

  private static final Set<String> VALID_ProviderS = Set.of("Agoda", "Booking", "Expedia");
  private static final int MAX_HOTEL_NAME_LENGTH = 255;
  private static final int MAX_REVIEW_COMMENTS_LENGTH = 5000;
  private static final double MIN_RATING = 0.0;
  private static final double MAX_RATING = 10.0;
  private static final int MAX_REVIEW_AGE_YEARS = 20;

  // private static final Logger log = LoggerFactory.getLogger(ReviewDataValidator.class);

  /** Validates raw review data and returns validation result */
  public ValidationResult validate(RawReviewData reviewData) {
    if (reviewData == null) {
      return ValidationResult.invalid(List.of("Review data cannot be null"));
    }

    List<String> errors = new ArrayList<>();

    // Validate hotel ID
    validateHotelId(reviewData.getHotelId(), errors);

    // Validate provider
    validateProvider(reviewData.getProvider(), errors);

    // Validate hotel name
    validateHotelName(reviewData.getHotelName(), errors);

    // Validate comment section
    validateComment(reviewData.getComment(), errors);

    if (errors.isEmpty()) {
      log.debug("Validation passed for review on line {}", reviewData.getLineNumber());
      return ValidationResult.valid();
    } else {
      log.warn(
          "Validation failed for review on line {} with errors: {}",
          reviewData.getLineNumber(),
          errors);
      return ValidationResult.invalid(errors);
    }
  }

  /** Validates hotel ID */
  private void validateHotelId(Integer hotelId, List<String> errors) {
    if (hotelId == null) {
      errors.add("Hotel ID is required");
    } else if (hotelId <= 0) {
      errors.add("Hotel ID must be positive");
    }
  }

  /** Validates provider */
  private void validateProvider(String provider, List<String> errors) {
    if (provider == null) {
      errors.add("Provider is required");
    } else if (provider.trim().isEmpty()) {
      errors.add("Provider cannot be empty");
    } else if (!VALID_ProviderS.contains(provider)) {
      errors.add("Provider must be one of: " + String.join(", ", VALID_ProviderS));
    }
  }

  /** Validates hotel name */
  private void validateHotelName(String hotelName, List<String> errors) {
    if (hotelName == null) {
      errors.add("Hotel name is required");
    } else if (hotelName.trim().isEmpty()) {
      errors.add("Hotel name cannot be empty");
    } else if (hotelName.length() > MAX_HOTEL_NAME_LENGTH) {
      errors.add("Hotel name cannot exceed " + MAX_HOTEL_NAME_LENGTH + " characters");
    }
  }

  /** Validates comment section */
  private void validateComment(Map<String, Object> comment, List<String> errors) {
    if (comment == null) {
      errors.add("Comment section is required");
      return;
    }

    // Validate rating
    validateRating(comment.get("rating"), errors);

    // Validate review date
    validateReviewDate(comment.get("reviewDate"), errors);

    // Validate hotel review ID
    validateHotelReviewId(comment.get("hotelReviewId"), errors);

    // Validate review comments length
    validateReviewComments(comment.get("reviewComments"), errors);

    // Validate reviewer info if present
    validateReviewerInfo(comment.get("reviewerInfo"), errors);
  }

  /** Validates rating */
  private void validateRating(Object rating, List<String> errors) {
    if (rating == null) {
      // Rating is optional in some cases
      return;
    }

    try {
      double ratingValue = convertToDouble(rating);
      if (ratingValue < MIN_RATING || ratingValue > MAX_RATING) {
        errors.add("Rating must be between " + MIN_RATING + " and " + MAX_RATING);
      }
    } catch (NumberFormatException e) {
      errors.add("Rating must be a valid number");
    }
  }

  /** Validates review date */
  private void validateReviewDate(Object reviewDate, List<String> errors) {
    if (reviewDate == null) {
      errors.add("Review date is required");
      return;
    }

    try {
      String dateString = reviewDate.toString();
      LocalDateTime parsedDate = parseDateTime(dateString);

      if (parsedDate.isAfter(LocalDateTime.now())) {
        errors.add("Review date cannot be in the future");
      }

      if (parsedDate.isBefore(LocalDateTime.now().minusYears(MAX_REVIEW_AGE_YEARS))) {
        errors.add("Review date cannot be older than " + MAX_REVIEW_AGE_YEARS + " years");
      }

    } catch (DateTimeParseException e) {
      errors.add("Invalid review date format");
    }
  }

  /** Validates hotel review ID */
  private void validateHotelReviewId(Object hotelReviewId, List<String> errors) {
    if (hotelReviewId != null) {
      try {
        long reviewId = convertToLong(hotelReviewId);
        if (reviewId <= 0) {
          errors.add("Hotel review ID must be positive");
        }
      } catch (NumberFormatException e) {
        errors.add("Hotel review ID must be a valid number");
      }
    }
  }

  /** Validates review comments length */
  private void validateReviewComments(Object reviewComments, List<String> errors) {
    if (reviewComments != null) {
      String comments = reviewComments.toString();
      if (comments.length() > MAX_REVIEW_COMMENTS_LENGTH) {
        errors.add("Review comments cannot exceed " + MAX_REVIEW_COMMENTS_LENGTH + " characters");
      }
    }
  }

  /** Validates reviewer info if present */
  @SuppressWarnings("unchecked")
  private void validateReviewerInfo(Object reviewerInfo, List<String> errors) {
    if (reviewerInfo != null && reviewerInfo instanceof Map) {
      Map<String, Object> info = (Map<String, Object>) reviewerInfo;

      // Validate country name if present
      Object countryName = info.get("countryName");
      if (countryName != null && countryName.toString().trim().isEmpty()) {
        errors.add("Country name cannot be empty when present");
      }

      // Validate length of stay if present
      Object lengthOfStay = info.get("lengthOfStay");
      if (lengthOfStay != null) {
        try {
          int stay = convertToInt(lengthOfStay);
          if (stay <= 0) {
            errors.add("Length of stay must be positive when present");
          }
        } catch (NumberFormatException e) {
          errors.add("Length of stay must be a valid number when present");
        }
      }
    }
  }

  /** Parses date time from various formats */
  private LocalDateTime parseDateTime(String dateString) {
    // Try common date formats
    List<DateTimeFormatter> formatters =
        List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    for (DateTimeFormatter formatter : formatters) {
      try {
        if (dateString.length() == 10) { // Date only format
          return LocalDateTime.parse(
              dateString + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return LocalDateTime.parse(dateString, formatter);
      } catch (DateTimeParseException e) {
        // Try next formatter
        log.warn("error while parsing error", e);
      }
    }

    throw new DateTimeParseException("Unable to parse date: " + dateString, dateString, 0);
  }

  /** Converts object to double */
  private double convertToDouble(Object value) {
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    } else if (value instanceof String) {
      return Double.parseDouble((String) value);
    } else {
      throw new NumberFormatException("Cannot convert to double: " + value);
    }
  }

  /** Converts object to long */
  private long convertToLong(Object value) {
    if (value instanceof Number) {
      return ((Number) value).longValue();
    } else if (value instanceof String) {
      return Long.parseLong((String) value);
    } else {
      throw new NumberFormatException("Cannot convert to long: " + value);
    }
  }

  /** Converts object to int */
  private int convertToInt(Object value) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    } else if (value instanceof String) {
      return Integer.parseInt((String) value);
    } else {
      throw new NumberFormatException("Cannot convert to int: " + value);
    }
  }
}
