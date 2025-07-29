package com.reviewsystem.domain.entity;

import com.reviewsystem.common.enums.ProviderType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * provider entity representing external review data sources Supports multiple providers with
 * different configurations and rating scales
 */
@Entity
@Table(
    name = "providers",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = {"code"},
          name = "uk_provider_code"),
      @UniqueConstraint(
          columnNames = {"name"},
          name = "uk_provider_name")
    },
    indexes = {
      @Index(name = "idx_provider_active", columnList = "active"),
      @Index(name = "idx_provider_priority", columnList = "processing_priority")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provider {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** provider display name */
  @Column(name = "name", nullable = false, length = 100)
  @NotBlank(message = "provider name is required")
  @Size(max = 100, message = "provider name must not exceed 100 characters")
  private String name;

  /** Unique provider code (uppercase) */
  @Column(name = "code", nullable = false, length = 10, unique = true)
  private ProviderType code;

  /** Externally linked id e.g. 322 etc as per json */
  @Column(name = "external_id", unique = true) // Maps to 'external_id' column, unique
  private Integer externalId;

  /** provider description */
  @Column(name = "description", length = 500)
  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  /** API endpoint URL for the provider */
  @Column(name = "api_endpoint", length = 255)
  @Size(max = 255, message = "API endpoint must not exceed 255 characters")
  @Pattern(
      regexp = "^(https?://)[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$",
      message = "API endpoint must be a valid URL")
  private String apiEndpoint;

  /** Whether the provider is currently active */
  @Column(name = "active", nullable = false)
  @NotNull
  @Builder.Default
  private Boolean active = true;

  /** provider's rating scale (e.g., 5.0 for 1-5 scale, 10.0 for 1-10 scale) */
  @Column(name = "rating_scale")
  @DecimalMin(value = "1.0", message = "Rating scale must be between 1.0 and 10.0")
  @DecimalMax(value = "10.0", message = "Rating scale must be between 1.0 and 10.0")
  @Builder.Default
  private Double ratingScale = 5.0;

  /** Comma-separated list of supported translateSource codes */
  @Column(name = "supported_translateSources", length = 200)
  @Size(max = 200, message = "Supported translateSources must not exceed 200 characters")
  @Builder.Default
  private String supportedLanguages = "en";

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

  /** Reviews associated with this provider */
  @OneToMany(mappedBy = "provider", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
  @Builder.Default
  private Set<Review> reviews = new LinkedHashSet<>();

  // Business Logic Methods

  /** Checks if the provider is currently active */
  public boolean isActive() {
    return Boolean.TRUE.equals(active);
  }

  /** Gets the total number of reviews for this provider */
  public int getTotalReviewCount() {
    return reviews.size();
  }

  /** Calculates the average rating for all reviews from this provider */
  public double getAverageRating() {
    if (reviews.isEmpty()) {
      return 0.0;
    }

    return reviews.stream()
        .filter(review -> review.getRating() != null)
        .mapToDouble(Review::getRating)
        .average()
        .orElse(0.0);
  }

  /** Checks if the provider supports a specific translateSource */
  public boolean supportsLanguage(String translateSourceCode) {
    if (supportedLanguages == null || translateSourceCode == null) {
      return false;
    }

    String[] translateSources = supportedLanguages.toLowerCase().split(",");
    return Arrays.stream(translateSources)
        .map(String::trim)
        .anyMatch(lang -> lang.equals(translateSourceCode.toLowerCase()));
  }

  /** Normalizes a rating from provider's scale to standard 5-point scale */
  public double normalizeRating(double originalRating) {
    if (ratingScale == null || ratingScale <= 0) {
      return originalRating;
    }

    // Convert to 5-point scale: (originalRating / providerScale) * 5
    return (originalRating / ratingScale) * 5.0;
  }

  /** Activates the provider */
  public void activate() {
    this.active = true;
  }

  /** Deactivates the provider */
  public void deactivate() {
    this.active = false;
  }

  // Relationship Management
  /** Adds a review to this provider */
  public void addReview(Review review) {
    if (review == null) return;
    // Set provider first, so hashCode/equals are stable before adding to set
    if (review.getProvider() != this) {
      review.setProvider(this);
    }
    // Now add to set if not already present
    if (!reviews.contains(review)) {
      reviews.add(review);
    }
  }

  /** Removes a review from this provider */
  public void removeReview(Review review) {
    if (review != null && reviews.contains(review)) {
      reviews.remove(review);
      review.setProvider(null);
    }
  }

  /** Clears all reviews (use with caution) */
  public void clearReviews() {
    reviews.forEach(review -> review.setProvider(null));
    reviews.clear();
  }

  // Standard Object Methods

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Provider provider = (Provider) o;
    if (id != null && provider.id != null) {
      return id.equals(provider.id);
    }
    // Fallback to business key if both ids are null
    return code != null && code.equals(provider.code);
  }

  @Override
  public int hashCode() {
    if (id != null) return id.hashCode();
    return code != null ? code.hashCode() : 0;
  }

  @Override
  public String toString() {
    return String.format(
        "provider{id=%d, name='%s', code='%s', active=%s, externalId=%d, reviewCount=%d}",
        id, name, code, active, externalId, getTotalReviewCount());
  }
}
