package com.reviewsystem.infrastructure.parser;

import com.reviewsystem.domain.entity.Review;
import com.reviewsystem.domain.entity.Provider;
import com.reviewsystem.infrastructure.parser.dto.RawReviewData;
import com.reviewsystem.common.util.HashUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class ReviewDataTransformer {

    private static final DateTimeFormatter[] SUPPORTED_DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    /**
     * Transform raw review data into Review entity
     */
    public Review transform(RawReviewData rawData, Provider provider) {
        try {
            log.debug("Transforming raw review data for hotel ID: {}, provider: {}",
                    rawData.getHotelId(), provider.getName());

            Review review = Review.builder()
                    .hotelId(rawData.getHotelId())
                    .hotelName(sanitizeHotelName(rawData.getHotelName()))
                    .provider(provider)
                    .providerReviewId(extractproviderReviewId(rawData))
                    .rating(extractRating(rawData))
                    .reviewText(extractcomment(rawData))
                    .reviewDate(extractReviewDate(rawData))
                    .reviewerName(extractReviewerName(rawData))
                    .reviewTitle(extractReviewTitle(rawData))
                    .contentHash(generateContentHash(rawData))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // Extract additional metadata if available
            extractAndSetMetadata(review, rawData);

            log.debug("Successfully transformed review with ID: {} for hotel: {}",
                    review.getProviderExternalId(), review.getHotelName());

            return review;

        } catch (Exception e) {
            log.error("Error transforming raw review data for hotel ID: {}, provider: {}",
                    rawData.getHotelId(), provider.getName(), e);
            throw new RuntimeException("Failed to transform review data: " + e.getMessage(), e);
        }
    }

    /**
     * Extract provider review ID from raw data
     */
    private String extractproviderReviewId(RawReviewData rawData) {
        Map<String, Object> comment = rawData.getComment();
        if (comment == null) {
            throw new IllegalArgumentException("Comment data is null");
        }

        Object reviewId = comment.get("hotelReviewId");
        if (reviewId == null) {
            throw new IllegalArgumentException("hotelReviewId is missing from comment data");
        }

        return String.valueOf(reviewId);
    }

    /**
     * Extract rating from raw data
     */
    private Double extractRating(RawReviewData rawData) {
        Map<String, Object> comment = rawData.getComment();
        if (comment == null) {
            return null;
        }

        Object ratingObj = comment.get("rating");
        if (ratingObj == null) {
            return null;
        }

        try {
            if (ratingObj instanceof Number) {
                double rating = ((Number) ratingObj).doubleValue();

                // Validate rating range (assuming 0-10 scale)
                if (rating < 0 || rating > 10) {
                    log.warn("Rating {} is outside expected range (0-10)", rating);
                }

                return rating;
            } else if (ratingObj instanceof String) {
                return Double.parseDouble((String) ratingObj);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid rating format: {}", ratingObj, e);
        }

        return null;
    }

    /**
     * Extract review text from raw data
     */
    private String extractcomment(RawReviewData rawData) {
        Map<String, Object> comment = rawData.getComment();
        if (comment == null) {
            return null;
        }

        Object reviewComments = comment.get("reviewComments");
        if (reviewComments == null) {
            return null;
        }

        String text = String.valueOf(reviewComments).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Extract review date from raw data
     */
    private LocalDateTime extractReviewDate(RawReviewData rawData) {
        Map<String, Object> comment = rawData.getComment();
        if (comment == null) {
            return null;
        }

        Object reviewDateObj = comment.get("reviewDate");
        if (reviewDateObj == null) {
            return null;
        }

        String dateString = String.valueOf(reviewDateObj);

        // Try different date formats
        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATS) {
            try {
                if (formatter == DateTimeFormatter.ISO_ZONED_DATE_TIME ||
                        formatter.toString().contains("XXX")) {
                    // Handle zoned date time
                    ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateString, formatter);
                    return zonedDateTime.toLocalDateTime();
                } else {
                    // Handle local date time
                    return LocalDateTime.parse(dateString, formatter);
                }
            } catch (DateTimeParseException e) {
                // Try next format
                continue;
            }
        }

        log.warn("Unable to parse date: {}", dateString);
        return null;
    }

    /**
     * Extract reviewer name from raw data
     */
    private String extractReviewerName(RawReviewData rawData) {
        Map<String, Object> comment = rawData.getComment();
        if (comment == null) {
            return null;
        }

        Object reviewerName = comment.get("reviewerName");
        if (reviewerName == null) {
            return null;
        }

        String name = String.valueOf(reviewerName).trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * Extract review title from raw data
     */
    private String extractReviewTitle(RawReviewData rawData) {
        Map<String, Object> comment = rawData.getComment();
        if (comment == null) {
            return null;
        }

        Object reviewTitle = comment.get("reviewTitle");
        if (reviewTitle == null) {
            return null;
        }

        String title = String.valueOf(reviewTitle).trim();
        return title.isEmpty() ? null : title;
    }

    /**
     * Sanitize hotel name
     */
    private String sanitizeHotelName(String hotelName) {
        if (hotelName == null) {
            return null;
        }

        String sanitized = hotelName.trim();

        // Remove extra whitespaces
        sanitized = sanitized.replaceAll("\\s+", " ");

        return sanitized.isEmpty() ? null : sanitized;
    }

    /**
     * Generate content hash for duplicate detection
     */
    private String generateContentHash(RawReviewData rawData) {
        StringBuilder contentBuilder = new StringBuilder();

        contentBuilder.append(rawData.getHotelId());
        contentBuilder.append("|");

        if (rawData.getComment() != null) {
            Object comment = rawData.getComment().get("reviewComments");
            if (comment != null) {
                contentBuilder.append(comment.toString().trim().toLowerCase());
            }
            contentBuilder.append("|");

            Object rating = rawData.getComment().get("rating");
            if (rating != null) {
                contentBuilder.append(rating.toString());
            }
            contentBuilder.append("|");

            Object reviewDate = rawData.getComment().get("reviewDate");
            if (reviewDate != null) {
                contentBuilder.append(reviewDate.toString());
            }
        }

        return HashUtil.generateSHA256Hash(contentBuilder.toString());
    }

    /**
     * Extract and set additional metadata
     */
    private void extractAndSetMetadata(Review review, RawReviewData rawData) {
        Map<String, Object> comment = rawData.getComment();
        if (comment == null) {
            return;
        }

        // Extract additional fields that might be present
        Object helpfulVotes = comment.get("helpfulVotes");
        if (helpfulVotes instanceof Number) {
            review.setHelpfulVotes(((Number) helpfulVotes).intValue());
        }

        Object totalVotes = comment.get("totalVotes");
        if (totalVotes instanceof Number) {
            review.setTotalVotes(((Number) totalVotes).intValue());
        }

        Object isVerified = comment.get("isVerified");
        if (isVerified instanceof Boolean) {
            review.setIsVerified((Boolean) isVerified);
        }

        Object reviewLanguage = comment.get("language");
        if (reviewLanguage != null) {
            review.setLanguage(String.valueOf(reviewLanguage));
        }

        // Set line number for tracking
        review.setSourceLineNumber(rawData.getLineNumber());
    }

    /**
     * Validate transformed review
     */
    public boolean isValidTransformedReview(Review review) {
        return review != null &&
                review.getHotelId() != null &&
                review.getProvider() != null &&
                review.getProviderExternalId() != null;
    }
}