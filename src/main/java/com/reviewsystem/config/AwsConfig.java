package com.reviewsystem.config;

import com.reviewsystem.infrastructure.aws.S3Config;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** AWS configuration for S3 client setup */
@Configuration
@EnableConfigurationProperties(S3Config.class)
@EnableRetry
public class AwsConfig {

  private final S3Config s3Config;

  public AwsConfig(S3Config s3Config) {
    this.s3Config = s3Config;
  }

  /** Creates and configures the S3Client bean Supports both AWS and LocalStack configurations */
  @Bean
  public S3Client s3Client() {
    S3ClientBuilder builder = S3Client.builder().region(Region.of(s3Config.getRegion()));

    // Set credentials if provided
    if (s3Config.getAccessKey() != null && s3Config.getSecretKey() != null) {
      AwsBasicCredentials credentials =
          AwsBasicCredentials.create(s3Config.getAccessKey(), s3Config.getSecretKey());
      builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
    }

    // Configure for LocalStack or custom endpoint
    if (s3Config.getEndpoint() != null && !s3Config.getEndpoint().isEmpty()) {
      builder.endpointOverride(URI.create(s3Config.getEndpoint()));

      // LocalStack typically requires path-style access
      if (s3Config.getPathStyleAccess() || s3Config.isLocalStack()) {
        builder.forcePathStyle(true);
      }
    }

    return builder.build();
  }
}
