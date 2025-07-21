package com.reviewsystem.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Provider entity representing external review data sources Supports multiple providers with
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
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provider {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Provider display name */
  @Column(name = "name", nullable = false, length = 100)
  @NotBlank(message = "Provider name is required")
  @Size(max = 100, message = "Provider name must not exceed 100 characters")
  private String name;

  /** Unique provider code (uppercase) */
  @Column(name = "code", nullable = false, length = 10)
  @NotBlank(message = "Provider code is required")
  @Size(min = 2, max = 10, message = "Provider code must be between 2 and 10 characters")
  @Pattern(regexp = "^[A-Z]+$", message = "Provider code must be uppercase letters only")
  private String code;

  /** Provider description */
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

  /** Provider's rating scale (e.g., 5.0 for 1-5 scale, 10.0 for 1-10 scale) */
  @Column(name = "rating_scale", precision = 3, scale = 1)
  @DecimalMin(value = "1.0", message = "Rating scale must be between 1.0 and 10.0")
  @DecimalMax(value = "10.0", message = "Rating scale must be between 1.0 and 10.0")
  @Builder.Default
  private Double ratingScale = 5.0;

  /** Comma-separated list of supported language codes */
  @Column(name = "supported_languages", length = 200)
  @Size(max = 200, message = "Supported languages must not exceed 200 characters")
  @Builder.Default
  private String supportedLanguages = "en";

  /** Processing priority (1 = highest, 10 = lowest) */
  @Column(name = "processing_priority")
  @Min(value = 1, message = "Processing priority must be between 1 and 10")
  @Max(value = 10, message = "Processing priority must be between 1 and 10")
  @Builder.Default
  private Integer processingPriority = 5;

  /** Maximum file size in MB that can be processed */
  @Column(name = "max_file_size_mb")
  @Min(value = 1, message = "Maximum file size must be at least 1 MB")
  @Builder.Default
  private Integer maxFileSizeMb = 100;

  /** Batch size for processing reviews */
  @Column(name = "batch_size")
  @Min(value = 1, message = "Batch size must be at least 1")
  @Max(value = 10000, message = "Batch size must not exceed 10000")
  @Builder.Default
  private Integer batchSize = 1000;

  /** S3 bucket path for this provider's files */
  @Column(name = "s3_path", length = 200)
  @Size(max = 200, message = "S3 path must not exceed 200 characters")
  private String s3Path;

  /** File naming pattern regex */
  @Column(name = "file_pattern", length = 100)
  @Size(max = 100, message = "File pattern must not exceed 100 characters")
  @Builder.Default
  private String filePattern = ".*\\.jl$";

  /** Timezone for date processing */
  @Column(name = "timezone", length = 50)
  @Size(max = 50, message = "Timezone must not exceed 50 characters")
  @Builder.Default
  private String timezone = "UTC";

  /** Last successful processing timestamp */
  @Column(name = "last_processed_at")
  private LocalDateTime lastProcessedAt;

  /** Last successful file processed */
  @Column(name = "last_processed_file", length = 255)
  @Size(max = 255, message = "Last processed file must not exceed 255 characters")
  private String lastProcessedFile;

  /** Total number of reviews processed */
  @Column(name = "total_reviews_processed")
  @Builder.Default
  private Long totalReviewsProcessed = 0L;

  /** Configuration in JSON format */
  @Column(name = "configuration", columnDefinition = "TEXT")
  private String configuration;

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
  // ToDO: Make set of actual Review class
  private Set<String> reviews = new LinkedHashSet<>();

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

    // ToDO: replace with logic from Review once Review is implemented
    return 2.5;
  }

  /** Checks if the provider supports a specific language */
  public boolean supportsLanguage(String languageCode) {
    if (supportedLanguages == null || languageCode == null) {
      return false;
    }

    String[] languages = supportedLanguages.toLowerCase().split(",");
    return Arrays.stream(languages)
        .map(String::trim)
        .anyMatch(lang -> lang.equals(languageCode.toLowerCase()));
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

  /** Updates the last processed timestamp to now */
  public void updateLastProcessedTimestamp() {
    this.lastProcessedAt = LocalDateTime.now();
  }

  /** Updates processing statistics */
  public void updateProcessingStats(String fileName, int reviewsProcessed) {
    this.lastProcessedFile = fileName;
    this.lastProcessedAt = LocalDateTime.now();
    this.totalReviewsProcessed += reviewsProcessed;
  }

  // ToDo: Relationship Management

  // Standard Object Methods

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    Provider provider = (Provider) obj;

    // Use business key (code) for equality if available
    if (code != null && provider.code != null) {
      return Objects.equals(code, provider.code);
    }

    // Fallback to ID comparison
    return Objects.equals(id, provider.id) && id != null;
  }

  @Override
  public int hashCode() {
    if (code != null) {
      return Objects.hash(code);
    }
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return String.format(
        "Provider{id=%d, name='%s', code='%s', active=%s, ratingScale=%.1f, reviewCount=%d}",
        id, name, code, active, ratingScale, getTotalReviewCount());
  }
}
