package com.reviewsystem.infrastructure.aws;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/** Configuration properties for AWS S3 integration */
@ConfigurationProperties(prefix = "aws.s3")
@Validated
public class S3Config {

  @NotBlank(message = "S3 bucket name cannot be blank")
  private final String bucketName;

  @NotBlank(message = "S3 region cannot be blank")
  private final String region;

  @NotNull(message = "S3 prefix cannot be null")
  private final String prefix;

  @NotNull(message = "Access key cannot be null")
  private final String accessKey;

  @NotNull(message = "Secret key cannot be null")
  private final String secretKey;

  @Positive(message = "Max retries must be positive")
  private final Integer maxRetries;

  @Positive(message = "Retry delay must be positive")
  private final Long retryDelayMs;

  @Positive(message = "Connection timeout must be positive")
  private final Duration connectionTimeout;

  @Positive(message = "Read timeout must be positive")
  private final Duration readTimeout;

  private final Boolean pathStyleAccess;

  private final String endpoint;

  @ConstructorBinding
  public S3Config(
      String bucketName,
      String region,
      String prefix,
      String accessKey,
      String secretKey,
      Integer maxRetries,
      Long retryDelayMs,
      Duration connectionTimeout,
      Duration readTimeout,
      Boolean pathStyleAccess,
      String endpoint) {

    this.bucketName = bucketName;
    this.region = region != null ? region : "us-east-1";
    this.prefix = prefix != null ? prefix : "";
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.maxRetries = maxRetries != null ? maxRetries : 3;
    this.retryDelayMs = retryDelayMs != null ? retryDelayMs : 1000L;
    this.connectionTimeout = connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(30);
    this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(60);
    this.pathStyleAccess = pathStyleAccess != null ? pathStyleAccess : false;
    this.endpoint = endpoint;
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getRegion() {
    return region;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public Integer getMaxRetries() {
    return maxRetries;
  }

  public Long getRetryDelayMs() {
    return retryDelayMs;
  }

  public Duration getConnectionTimeout() {
    return connectionTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public Boolean getPathStyleAccess() {
    return pathStyleAccess;
  }

  public String getEndpoint() {
    return endpoint;
  }

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

  @Override
  public String toString() {
    return "S3Config{"
        + "bucketName='"
        + bucketName
        + '\''
        + ", region='"
        + region
        + '\''
        + ", prefix='"
        + prefix
        + '\''
        + ", accessKey='***'"
        + ", secretKey='***'"
        + ", maxRetries="
        + maxRetries
        + ", retryDelayMs="
        + retryDelayMs
        + ", connectionTimeout="
        + connectionTimeout
        + ", readTimeout="
        + readTimeout
        + ", pathStyleAccess="
        + pathStyleAccess
        + ", endpoint='"
        + endpoint
        + '\''
        + '}';
  }
}
