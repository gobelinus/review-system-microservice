package com.reviewsystem.infrastructure.aws;

import com.reviewsystem.presentation.exception.FileProcessingException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Service for interacting with AWS S3 to manage review files */
@Service
public class S3Service {

  private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

  private final S3Client s3Client;
  private final S3Config s3Config;

  public S3Service(S3Client s3Client, S3Config s3Config) {
    this.s3Client = Objects.requireNonNull(s3Client, "S3Client cannot be null");
    this.s3Config = Objects.requireNonNull(s3Config, "S3Config cannot be null");
  }

  /**
   * Lists all files in the S3 bucket with the configured prefix Handles pagination automatically
   *
   * @return List of file metadata
   * @throws FileProcessingException if unable to list files
   */
  public List<S3FileMetadata> listFiles() {
    logger.debug(
        "Listing files from bucket: {} with prefix: {}",
        s3Config.getBucketName(),
        s3Config.getPrefix());

    try {
      List<S3FileMetadata> allFiles = new ArrayList<>();

      ListObjectsV2Request request =
          ListObjectsV2Request.builder()
              .bucket(s3Config.getBucketName())
              .prefix(s3Config.getNormalizedPrefix())
              .build();

      s3Client.listObjectsV2Paginator(request).stream()
          .flatMap(response -> response.contents().stream())
          .filter(this::isValidFile)
          .forEach(s3Object -> allFiles.add(convertToFileMetadata(s3Object)));

      logger.info("Found {} files in S3 bucket", allFiles.size());
      return allFiles;

    } catch (SdkException e) {
      logger.error("Failed to list files from S3 bucket: {}", s3Config.getBucketName(), e);
      throw new FileProcessingException("Failed to list files from S3: " + e.getMessage(), e);
    }
  }

  /**
   * Lists files with optional additional prefix filter
   *
   * @param additionalPrefix Additional prefix to filter files
   * @return List of file metadata
   */
  public List<S3FileMetadata> listFiles(String additionalPrefix) {
    String fullPrefix =
        s3Config.getNormalizedPrefix() + (additionalPrefix != null ? additionalPrefix : "");

    logger.debug("Listing files with full prefix: {}", fullPrefix);

    try {
      List<S3FileMetadata> allFiles = new ArrayList<>();

      ListObjectsV2Request request =
          ListObjectsV2Request.builder()
              .bucket(s3Config.getBucketName())
              .prefix(fullPrefix)
              .build();

      s3Client.listObjectsV2Paginator(request).stream()
          .flatMap(response -> response.contents().stream())
          .filter(this::isValidFile)
          .forEach(s3Object -> allFiles.add(convertToFileMetadata(s3Object)));

      return allFiles;

    } catch (SdkException e) {
      logger.error("Failed to list files from S3 with prefix: {}", fullPrefix, e);
      throw new FileProcessingException("Failed to list files from S3: " + e.getMessage(), e);
    }
  }

  /**
   * Downloads a file from S3 Includes retry mechanism for transient failures
   *
   * @param key The S3 object key
   * @return InputStream of the file content
   * @throws FileProcessingException if download fails
   */
  @Retryable(
      retryFor = {SdkException.class},
      maxAttempts = 4, // initial + 3 retries
      backoff = @Backoff(delayExpression = "#{@s3Config.retryDelayMs}", multiplier = 2.0))
  public InputStream downloadFile(String key) {
    validateFileKey(key);

    logger.debug("Downloading file: {} from bucket: {}", key, s3Config.getBucketName());

    try {
      GetObjectRequest request =
          GetObjectRequest.builder().bucket(s3Config.getBucketName()).key(key).build();

      InputStream inputStream = s3Client.getObject(request);
      logger.info("Successfully downloaded file: {}", key);
      return inputStream;

    } catch (NoSuchKeyException e) {
      logger.error("File not found: {}", key);
      throw new FileProcessingException("File not found: " + key, e);

    } catch (SdkException e) {
      logger.warn("Transient error downloading file: {} - {}", key, e.getMessage());
      throw e; // Let @Retryable handle this
    }
  }

  /** Recovery method for download failures after all retries are exhausted */
  @Recover
  public InputStream recoverDownload(SdkException ex, String key) {
    logger.error("Failed to download file after retries: {}", key, ex);
    throw new FileProcessingException("Failed to download file after retries: " + key, ex);
  }

  /**
   * Gets metadata for a specific file without downloading it
   *
   * @param key The S3 object key
   * @return File metadata
   * @throws FileProcessingException if file doesn't exist or metadata cannot be retrieved
   */
  public S3FileMetadata getFileMetadata(String key) {
    validateFileKey(key);

    logger.debug("Getting metadata for file: {}", key);

    try {
      HeadObjectRequest request =
          HeadObjectRequest.builder().bucket(s3Config.getBucketName()).key(key).build();

      HeadObjectResponse response = s3Client.headObject(request);

      return S3FileMetadata.builder()
          .key(key)
          .size(response.contentLength())
          .lastModified(response.lastModified())
          .eTag(response.eTag())
          .bucketName(s3Config.getBucketName())
          .build();

    } catch (NoSuchKeyException e) {
      logger.error("File metadata not found: {}", key);
      throw new FileProcessingException("File metadata not found: " + key, e);

    } catch (SdkException e) {
      logger.error("Failed to get metadata for file: {}", key, e);
      throw new FileProcessingException("Failed to get metadata for file: " + key, e);
    }
  }

  /**
   * Checks if a file exists in S3
   *
   * @param key The S3 object key
   * @return true if file exists, false otherwise
   */
  public boolean fileExists(String key) {
    validateFileKey(key);

    try {
      getFileMetadata(key);
      return true;
    } catch (FileProcessingException e) {
      return false;
    }
  }

  /**
   * Gets the total size of all files in the bucket with the configured prefix
   *
   * @return Total size in bytes
   */
  public long getTotalSize() {
    return listFiles().stream().mapToLong(S3FileMetadata::getSize).sum();
  }

  /** Validates that the file key is not null or empty */
  private void validateFileKey(String key) {
    if (key == null || key.trim().isEmpty()) {
      throw new IllegalArgumentException("File key cannot be null or empty");
    }
  }

  /** Converts S3Object to S3FileMetadata */
  private S3FileMetadata convertToFileMetadata(S3Object s3Object) {
    return S3FileMetadata.builder()
        .key(s3Object.key())
        .size(s3Object.size())
        .lastModified(s3Object.lastModified())
        .eTag(s3Object.eTag())
        .bucketName(s3Config.getBucketName())
        .build();
  }

  /** Filters out invalid files (directories, non-JSONL files, etc.) */
  private boolean isValidFile(S3Object s3Object) {
    // Skip directories (keys ending with '/')
    if (s3Object.key().endsWith("/")) {
      return false;
    }

    // Skip empty files
    if (s3Object.size() == 0) {
      logger.debug("Skipping empty file: {}", s3Object.key());
      return false;
    }

    // Only include .jl and .jsonl files
    String key = s3Object.key().toLowerCase();
    boolean isValidExtension = key.endsWith(".jl") || key.endsWith(".jsonl");

    if (!isValidExtension) {
      logger.debug("Skipping file with invalid extension: {}", s3Object.key());
    }

    return isValidExtension;
  }

  /** Gets configuration for monitoring/debugging purposes */
  public S3Config getConfig() {
    return s3Config;
  }
}
