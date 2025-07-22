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
          columnNames = {"external_id", "provider_id"},
          name = "uk_review_external_provider")
    },
    indexes = {
      @Index(name = "idx_review_property_id", columnList = "property_id"),
      @Index(name = "idx_review_rating", columnList = "rating"),
      @Index(name = "idx_review_date", columnList = "review_date"),
      @Index(name = "idx_review_language", columnList = "language"),
      @Index(name = "idx_review_created_at", columnList = "created_at")
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

  /** External identifier from the provider system */
  @Column(name = "external_id", nullable = false, length = 100)
  @NotBlank(message = "External ID is required")
  @Size(max = 100, message = "External ID must not exceed 100 characters")
  private String externalId;

  /** Provider that sourced this review */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "provider_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_review_provider"))
  @NotNull(message = "Provider is required")
  private Provider provider;

  /** Property/Hotel identifier */
  @Column(name = "property_id", nullable = false, length = 100)
  @NotBlank(message = "Property ID is required")
  @Size(max = 100, message = "Property ID must not exceed 100 characters")
  private String propertyId;

  /** Name of the guest who wrote the review */
  @Column(name = "guest_name", length = 200)
  @Size(max = 200, message = "Guest name must not exceed 200 characters")
  private String guestName;

  /** Rating score (normalized to 0.0-5.0 scale) */
  @Column(name = "rating", nullable = false)
  @NotNull(message = "Rating is required")
  @DecimalMin(value = "0.0", message = "Rating must be between 0.0 and 5.0")
  @DecimalMax(value = "5.0", message = "Rating must be between 0.0 and 5.0")
  private Double rating;

  /** Review text content */
  @Column(name = "review_text", columnDefinition = "TEXT")
  @Size(max = 10000, message = "Review text must not exceed 10000 characters")
  private String reviewText;

  /** Date when the review was originally posted */
  @Column(name = "review_date", nullable = false)
  @NotNull(message = "Review date is required")
  @PastOrPresent(message = "Review date cannot be in the future")
  private LocalDateTime reviewDate;

  /** Language code of the review (ISO 639-1) */
  @Column(name = "language", length = 5)
  @Pattern(
      regexp = "^[a-z]{2}(-[A-Z]{2})?$",
      message = "Language must be in ISO 639-1 format (e.g., 'en', 'en-US')")
  private String language;

  /** Optional title of the review */
  @Column(name = "title", length = 500)
  @Size(max = 500, message = "Title must not exceed 500 characters")
  private String title;

  /** Stay date if available */
  @Column(name = "stay_date")
  private LocalDateTime stayDate;

  /** Room type if specified in the review */
  @Column(name = "room_type", length = 200)
  @Size(max = 200, message = "Room type must not exceed 200 characters")
  private String roomType;

  /** Trip type (business, leisure, etc.) */
  @Column(name = "trip_type", length = 50)
  @Size(max = 50, message = "Trip type must not exceed 50 characters")
  private String tripType;

  /** Number of helpful votes */
  @Column(name = "helpful_votes")
  @Min(value = 0, message = "Helpful votes cannot be negative")
  private Integer helpfulVotes;

  /** Whether the review is verified */
  @Column(name = "verified", nullable = false)
  @Builder.Default
  private Boolean verified = false;

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

  /** Determines if the review is positive (rating >= 4.0) */
  public boolean isPositive() {
    return rating != null && rating >= 4.0;
  }

  /** Determines if the review is negative (rating < 3.0) */
  public boolean isNegative() {
    return rating != null && rating < 3.0;
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
    return reviewText != null && !reviewText.trim().isEmpty();
  }

  /** Generates a unique business key for deduplication */
  public String getBusinessKey() {
    if (provider == null || externalId == null) {
      return null;
    }
    return provider.getCode() + ":" + externalId;
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
    // Fallback: use externalId + provider code if both are set
    return externalId != null
        && externalId.equals(review.externalId)
        && provider != null
        && review.provider != null
        && provider.getCode() != null
        && provider.getCode().equals(review.provider.getCode());
  }

  @Override
  public int hashCode() {
    if (id != null) return id.hashCode();
    int result = externalId != null ? externalId.hashCode() : 0;
    result =
        31 * result
            + (provider != null && provider.getCode() != null ? provider.getCode().hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format(
        "Review{id=%d, externalId='%s', propertyId='%s', rating=%.2f, language='%s', processingStatus=%s}",
        id, externalId, propertyId, rating, language, processingStatus);
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
