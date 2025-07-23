package com.reviewsystem.infrastructure.aws;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration properties for AWS S3 service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "aws.s3")
@Validated
public class S3Config {

  /** S3 bucket name where review files are stored. */
  @NotBlank(message = "S3 bucket name cannot be blank")
  @Builder.Default
  private String bucketName = "";

  /** Prefix/folder path for review files in the S3 bucket. */
  @NotBlank(message = "Reviews prefix cannot be blank")
  @Builder.Default
  private String prefix = "reviews/";

  /** AWS region for S3 operations. */
  @NotBlank(message = "AWS region cannot be blank")
  @Builder.Default
  private String region = "us-east-1";

  @NotNull(message = "Access key cannot be null")
  private String accessKey;

  @NotNull(message = "Secret key cannot be null")
  private String secretKey;

  private String endpoint;

  /** Number of retry attempts for failed S3 operations. */
  @NotNull
  @Positive(message = "Retry attempts must be positive")
  @Builder.Default
  private Integer retryAttempts = 3;

  /** Delay between retry attempts in seconds. */
  @NotNull
  @Positive(message = "Retry delay must be positive")
  @Builder.Default
  private Integer retryDelaySeconds = 2;

  /** Maximum number of objects to fetch per S3 list request. */
  @NotNull
  @Positive(message = "Max keys per request must be positive")
  @Builder.Default
  private Integer maxKeysPerRequest = 1000;

  /** Connection timeout for S3 client in milliseconds. */
  @NotNull
  @Positive(message = "Connection timeout must be positive")
  @Builder.Default
  private Long connectionTimeoutMs = 5000L;

  /** Socket timeout for S3 client in milliseconds. */
  @NotNull
  @Positive(message = "Socket timeout must be positive")
  @Builder.Default
  private Long socketTimeoutMs = 30000L;

  /** Maximum number of connections in the connection pool. */
  @NotNull
  @Positive(message = "Max connections must be positive")
  @Builder.Default
  private Integer maxConnections = 50;

  @Builder.Default private Boolean pathStyleAccess = false;

  /** Check if this is a LocalStack configuration (for testing) */
  public boolean isLocalStack() {
    return endpoint != null && endpoint.contains("localstack");
  }

  /** Get the full prefix with trailing slash if needed */
  public String getNormalizedPrefix() {
    if (prefix == null || prefix.isEmpty()) {
      return "";
    }
    return prefix.endsWith("/") ? prefix : prefix + "/";
  }
}
