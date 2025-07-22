package com.reviewsystem.infrastructure.aws;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents metadata information for files stored in AWS S3.
 *
 * <p>This class encapsulates S3 file metadata including basic file information, S3-specific
 * properties, and custom metadata tags.
 *
 * <p>Uses Lombok annotations to reduce boilerplate code: - @Data: Generates getters, setters,
 * toString, equals, and hashCode - @Builder: Provides fluent builder pattern
 * - @NoArgsConstructor/@AllArgsConstructor: Generates constructors
 *
 * @author Review System Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class S3FileMetadata {

  /** The S3 bucket name where the file is stored */
  @EqualsAndHashCode.Include private String bucketName;

  /** The S3 object key (file path) within the bucket */
  @EqualsAndHashCode.Include private String key;

  /** File size in bytes */
  private Long size;

  /** Timestamp when the file was last modified */
  private Instant lastModified;

  /** S3 ETag for file integrity checking */
  private String eTag;
}
