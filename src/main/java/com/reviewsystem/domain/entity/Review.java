package com.reviewsystem.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Review entity representing a customer review from external providers Supports reviews from
 * multiple providers with multilingual content
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = {"provider_review_id", "provider_id"},
          name = "uk_review_provider_external")
    },
    indexes = {
      @Index(name = "idx_review_hotel_id", columnList = "hotel_id"),
      @Index(name = "idx_review_rating", columnList = "rating"),
      @Index(name = "idx_review_date", columnList = "review_date"),
      @Index(name = "idx_review_translateSource", columnList = "translateSource"),
      @Index(name = "idx_review_created_at", columnList = "created_at"),
      @Index(name = "idx_review_content_hash", columnList = "content_hash")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Hotel identifier */
  @Column(name = "hotel_id", nullable = false)
  @NotNull(message = "Hotel ID is required")
  private Integer hotelId;

  /** Hotel name */
  @Column(name = "hotel_name", length = 300)
  @Size(max = 300, message = "Hotel name must not exceed 300 characters")
  private String hotelName;

  /** Provider that sourced this review */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "provider_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_review_provider"))
  @NotNull(message = "Provider is required")
  private Provider provider;

  @Column(name = "platform", nullable = false, length = 100)
  private String platform;

  /** Provider review identifier (hotelReviewId from JSON) */
  @Column(name = "provider_review_id", nullable = false, length = 100)
  @NotBlank(message = "Provider review ID is required")
  @Size(max = 100, message = "Provider review ID must not exceed 100 characters")
  private String hotelReviewId;

  /** Provider ID from the JSON */
  @Column(name = "provider_external_id")
  private String providerExternalId;

  /** Rating score from the original provider scale */
  @Column(name = "rating", nullable = false)
  @NotNull(message = "Rating is required")
  @DecimalMin(value = "0.0", message = "Rating must be non-negative")
  @DecimalMax(value = "10.0", message = "Rating must not exceed 10.0")
  private Double rating;

  /** Formatted rating as provided by provider */
  @Column(name = "formatted_rating", length = 20)
  @Size(max = 20, message = "Formatted rating must not exceed 20 characters")
  private String formattedRating;

  /** Rating text description (e.g., "Good", "Excellent") */
  @Column(name = "rating_text", length = 50)
  @Size(max = 50, message = "Rating text must not exceed 50 characters")
  private String ratingText;

  /** Review text content (reviewComments from JSON) */
  @Column(name = "review_comments", columnDefinition = "TEXT")
  @Size(max = 10000, message = "Review text must not exceed 10000 characters")
  private String reviewComments;

  /** Review title */
  @Column(name = "review_title", length = 500)
  @Size(max = 500, message = "Review title must not exceed 500 characters")
  private String reviewTitle;

  /** Review negatives text */
  @Column(name = "review_negatives", columnDefinition = "TEXT")
  @Size(max = 5000, message = "Review negatives must not exceed 5000 characters")
  private String reviewNegatives;

  /** Review positives text */
  @Column(name = "review_positives", columnDefinition = "TEXT")
  @Size(max = 5000, message = "Review positives must not exceed 5000 characters")
  private String reviewPositives;

  /** Original title before translation */
  @Column(name = "original_title", length = 500)
  @Size(max = 500, message = "Original title must not exceed 500 characters")
  private String originalTitle;

  /** Original comment before translation */
  @Column(name = "original_comment", columnDefinition = "TEXT")
  @Size(max = 10000, message = "Original comment must not exceed 10000 characters")
  private String originalComment;

  /** Translation source translateSource */
  @Column(name = "translate_source", length = 10)
  @Size(max = 10, message = "Translate source must not exceed 10 characters")
  private String translateSource;

  /** Translation target translateSource */
  @Column(name = "translate_target", length = 10)
  @Size(max = 10, message = "Translate target must not exceed 10 characters")
  private String translateTarget;

  /** Content hash for duplicate detection */
  @Column(name = "content_hash", length = 64)
  @Size(max = 64, message = "Content hash must not exceed 64 characters")
  private String contentHash;

  /** Date when the review was originally posted */
  @Column(name = "review_date", nullable = false)
  @NotNull(message = "Review date is required")
  @PastOrPresent(message = "Review date cannot be in the future")
  private LocalDateTime reviewDate;

  /** Formatted review date as provided by provider */
  @Column(name = "formatted_review_date", length = 50)
  @Size(max = 50, message = "Formatted review date must not exceed 50 characters")
  private String formattedReviewDate;

  /** Check-in date month and year */
  @Column(name = "check_in_date_month_and_year", length = 50)
  @Size(max = 50, message = "Check-in date must not exceed 50 characters")
  private String checkinDate;

  // Reviewer Information
  @Column(name = "reviewer_display_name", length = 200)
  private String reviewerDisplayName;

  @Column(name = "reviewer_country_name", length = 100)
  private String reviewerCountryName;

  @Column(name = "reviewer_country_id")
  private Integer reviewerCountryId;

  @Column(name = "reviewer_flag_name", length = 10)
  private String reviewerFlagName;

  @Column(name = "review_group_name", length = 100)
  private String reviewGroupName;

  @Column(name = "review_group_id")
  private Integer reviewGroupId;

  @Column(name = "room_type_name", length = 200)
  private String roomTypeName;

  @Column(name = "room_type_id")
  private Integer roomTypeId;

  @Column(name = "length_of_stay")
  @Min(value = 0, message = "Length of stay cannot be negative")
  private Integer lengthOfStay;

  @Column(name = "is_show_global_icon", nullable = false)
  @Builder.Default // Default value as per table definition
  private Boolean isShowGlobalIcon = false;

  @Column(name = "is_show_reviewed_count", nullable = false)
  @Builder.Default // Default value as per table definition
  private Boolean isShowReviewedCount = false;

  // Response Information
  @Column(name = "is_show_review_response", nullable = false)
  @Builder.Default // Default value as per table definition
  private Boolean isShowReviewResponse = false;

  @Column(name = "responder_name", length = 200)
  private String responderName;

  @Column(name = "response_date_text", length = 100)
  private String responseDateText;

  @Column(name = "formatted_response_date", length = 100)
  private String formattedResponseDate;

  @Column(name = "response_translate_source", length = 10)
  private String responseTranslateSource;

  // Provider Specific
  @Column(name = "review_provider_logo", length = 500)
  private String reviewProviderLogo;

  @Column(name = "review_provider_text", length = 100)
  private String reviewProviderText;

  /** Room type from reviewerInfo */
  @Column(name = "room_type", length = 200)
  @Size(max = 200, message = "Room type must not exceed 200 characters")
  private String roomType;

  /** Review group (e.g., "Solo traveler", "Family") */
  @Column(name = "review_group", length = 100)
  @Size(max = 100, message = "Review group must not exceed 100 characters")
  private String reviewGroup;

  /** Number of reviews by this reviewer */
  @Column(name = "reviewer_review_count")
  @Min(value = 0, message = "Reviewer review count cannot be negative")
  private Integer reviewerReviewCount;

  /** Whether reviewer is an expert */
  @Column(name = "is_expert_reviewer")
  @Builder.Default
  private Boolean isExpertReviewer = false;

  /** Encrypted review data if available */
  @Column(name = "encrypted_review_data", length = 500)
  @Size(max = 500, message = "Encrypted data must not exceed 500 characters")
  private String encryptedReviewData;

  /** Processing status */
  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", length = 20, nullable = false)
  @Builder.Default
  private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

  /** Timestamp when record was created */
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** Timestamp when record was last updated */
  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** Version for optimistic locking */
  @Version private Long version;

  // Business Logic Methods
  /** Determines if the review is positive (rating >= 7.0 for 10-point scale) */
  public boolean isPositive() {
    return rating != null && rating >= 7.0;
  }

  /** Determines if the review is negative (rating < 5.0 for 10-point scale) */
  public boolean isNegative() {
    return rating != null && rating < 5.0;
  }

  /** Determines if the review is neutral (rating between 5.0 and 7.0) */
  public boolean isNeutral() {
    return rating != null && rating >= 5.0 && rating < 7.0;
  }

  /** Calculates the age of the review in days */
  public long getAgeInDays() {
    if (reviewDate == null) {
      return 0;
    }
    return ChronoUnit.DAYS.between(reviewDate, LocalDateTime.now());
  }

  /** Determines if the review is recent (within 30 days) */
  public boolean isRecent() {
    return getAgeInDays() <= 30;
  }

  /** Checks if the review has meaningful text content */
  public boolean hasTextContent() {
    return reviewComments != null && !reviewComments.trim().isEmpty();
  }

  /** Checks if the review has a title */
  public boolean hasTitle() {
    return reviewTitle != null && !reviewTitle.trim().isEmpty();
  }

  /** Generates a unique business key for deduplication */
  public String getBusinessKey() {
    if (provider == null || hotelReviewId == null) {
      return null;
    }
    return provider.getName() + ":" + hotelReviewId;
  }

  /** Marks the review as processed */
  public void markAsProcessed() {
    this.processingStatus = ProcessingStatus.PROCESSED;
  }

  /** Marks the review as failed processing */
  public void markAsFailed() {
    this.processingStatus = ProcessingStatus.FAILED;
  }

  /** Checks if processing is complete */
  public boolean isProcessed() {
    return ProcessingStatus.PROCESSED.equals(this.processingStatus);
  }

  /** Checks if the review is a solo traveler review */
  public boolean isSoloTraveler() {
    return "Solo traveler".equalsIgnoreCase(reviewGroup);
  }

  /** Checks if the reviewer is experienced (has multiple reviews) */
  public boolean isExperiencedReviewer() {
    return reviewerReviewCount != null && reviewerReviewCount > 5;
  }

  // Relationship Management
  /** Sets the provider and maintains bidirectional relationship */
  public void setProvider(Provider provider) {
    if (this.provider == provider) return;
    if (this.provider != null) {
      this.provider.getReviews().remove(this);
    }
    this.provider = provider;
    if (provider != null && !provider.getReviews().contains(this)) {
      provider.getReviews().add(this);
    }
  }

  // Standard Object Methods

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Review review = (Review) o;
    if (id != null && review.id != null) {
      return id.equals(review.id);
    }
    // Fallback: use hotelReviewId + provider name if both are set
    return hotelReviewId != null
        && hotelReviewId.equals(review.hotelReviewId)
        && provider != null
        && review.provider != null
        && provider.getName() != null
        && provider.getName().equals(review.provider.getName());
  }

  @Override
  public int hashCode() {
    if (id != null) return id.hashCode();
    int result = hotelReviewId != null ? hotelReviewId.hashCode() : 0;
    result =
        31 * result
            + (provider != null && provider.getName() != null ? provider.getName().hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format(
        "Review{id=%d, hotelReviewId='%s', hotelId=%d, hotelName='%s', rating=%.1f, translateSource='%s', processingStatus=%s}",
        id, hotelReviewId, hotelId, hotelName, rating, translateSource, processingStatus);
  }

  /** Processing status enumeration */
  public enum ProcessingStatus {
    PENDING("Pending processing"),
    PROCESSING("Currently being processed"),
    PROCESSED("Successfully processed"),
    FAILED("Processing failed"),
    SKIPPED("Skipped due to validation issues");

    private final String description;

    ProcessingStatus(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }
}
