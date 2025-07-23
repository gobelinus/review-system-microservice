package com.reviewsystem.infrastructure.aws;

import com.reviewsystem.common.constants.ApplicationConstants;
import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.entity.ProcessedFile;
import com.reviewsystem.domain.service.FileTrackingService;
import com.reviewsystem.presentation.exception.FileProcessingException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Service for handling AWS S3 operations for review files. This implementation will be created
 * based on the failing tests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

  private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
  private static final String REVIEWS_PREFIX = "reviews/";
  private static final String JSONL_EXTENSION = ".jl";

  private final S3Client s3Client;
  private final FileTrackingService fileTrackingService;
  private final S3Config s3Config;

  /** Validates that the file key is not null or empty */
  private void validateFileKey(String key) {
    if (key == null || key.trim().isEmpty()) {
      throw new IllegalArgumentException("File key cannot be null or empty");
    }
  }

  /**
   * Lists all unprocessed .jl files from the S3 bucket. Filters out files that have already been
   * processed.
   *
   * @return List of S3Object representing unprocessed files
   * @throws FileProcessingException if S3 operation fails
   */
  public List<S3Object> listUnprocessedFiles() {
    logger.debug(
        "Listing unprocessed files from bucket: {} with prefix: {}",
        s3Config.getBucketName(),
        REVIEWS_PREFIX);

    try {
      ListObjectsV2Request request =
          ListObjectsV2Request.builder()
              .bucket(s3Config.getBucketName())
              .prefix(REVIEWS_PREFIX)
              .build();

      return s3Client.listObjectsV2Paginator(request).stream()
          .flatMap(response -> response.contents().stream())
          .filter(this::isValidJsonLFile)
          .filter(s3Object -> !fileTrackingService.isFileProcessed(s3Object.key()))
          .toList();

    } catch (S3Exception e) {
      logger.error("Failed to list files from S3: {}", e.getMessage());
      throw new FileProcessingException("Failed to list files from S3", e);
    } catch (SdkException e) {
      logger.error("SDK error while listing files: {}", e.getMessage());
      throw new FileProcessingException("Failed to list files from S3", e);
    }
  }

  /**
   * Downloads a file from S3 bucket.
   *
   * @param fileKey The key of the file to download
   * @return InputStream of the file content
   * @throws FileProcessingException if download fails
   * @throws IllegalArgumentException if fileKey is null or empty
   */
  @Retryable(
      retryFor = {SdkException.class},
      maxAttempts = 4, // initial + 3 retries
      backoff = @Backoff(delayExpression = "#{@s3Config.retryDelayMs}", multiplier = 2.0))
  public InputStream downloadFile(String fileKey) {
    validateFileKey(fileKey);

    logger.debug("Downloading file: {} from bucket: {}", fileKey, s3Config.getBucketName());

    try {
      GetObjectRequest request =
          GetObjectRequest.builder().bucket(s3Config.getBucketName()).key(fileKey).build();

      InputStream inputStream = s3Client.getObject(request);
      logger.info("Successfully downloaded file: {}", fileKey);
      return inputStream;

    } catch (NoSuchKeyException e) {
      logger.error("File not found: {}", fileKey);
      throw new FileProcessingException("File not found: " + fileKey, e);
    } catch (S3Exception e) {
      throw new FileProcessingException(
          String.format(ApplicationConstants.ERROR_ACCESS_DENIED, fileKey), e);
    } catch (SdkException e) {
      if (e.getMessage() == "Network error occurred") {
        throw new FileProcessingException(ApplicationConstants.ERROR_NETWORK_FAILURE, e);
      }
      logger.warn("Transient error downloading file: {} - {}", fileKey, e.getMessage());
      throw e; // Let @Retryable handle this
    }
  }

  /**
   * Downloads a file from S3 with retry mechanism.
   *
   * @param fileKey The key of the file to download
   * @return InputStream of the file content
   * @throws FileProcessingException if download fails after all retries
   */
  public InputStream downloadFileWithRetry(String fileKey) {
    validateFileKey(fileKey);

    int maxRetries = 3;
    int attempt = 0;

    while (attempt < maxRetries) {
      try {
        logger.debug(
            "Attempting to download file: {} (attempt {}/{})", fileKey, attempt + 1, maxRetries);

        GetObjectRequest request =
            GetObjectRequest.builder().bucket(s3Config.getBucketName()).key(fileKey).build();

        InputStream inputStream = s3Client.getObject(request);
        logger.info("Successfully downloaded file: {} on attempt {}", fileKey, attempt + 1);
        return inputStream;

      } catch (NoSuchKeyException e) {
        logger.error("File not found: {}", fileKey);
        throw new FileProcessingException("File not found: " + fileKey, e);
      } catch (SdkException e) {
        attempt++;
        if (attempt >= maxRetries) {
          logger.error("Failed to download file {} after {} attempts", fileKey, maxRetries);
          throw new FileProcessingException(
              String.format("Failed to download file after %d attempts: %s", maxRetries, fileKey),
              e);
        }

        logger.warn("Retry attempt {} failed for file {}: {}", attempt, fileKey, e.getMessage());

        try {
          Thread.sleep(1000 * attempt); // Exponential backoff
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new FileProcessingException("Download interrupted", ie);
        }
      }
    }

    // This should never be reached, but included for completeness
    throw new FileProcessingException("Unexpected error during file download retry logic");
  }

  /**
   * Marks a file as successfully processed.
   *
   * @param fileKey The key of the processed file
   * @param fileSize Size of the file in bytes
   * @param recordsProcessed Number of records successfully processed
   * @param recordsSkipped Number of records skipped due to validation issues
   */
  public void markFileAsProcessed(
      String fileKey, long fileSize, int recordsProcessed, int recordsSkipped) {
    logger.debug("Marking file as processed: {}", fileKey);

    ProcessedFile processedFile =
        ProcessedFile.builder()
            .fileName(fileKey)
            .filePath(fileKey)
            .fileSize(fileSize)
            .processedAt(LocalDateTime.now())
            .status(ProcessingStatus.COMPLETED)
            .recordsProcessed(recordsProcessed)
            .recordsSkipped(recordsSkipped)
            .build();

    fileTrackingService.markFileAsProcessed(processedFile);
    logger.info("Successfully marked file as processed: {}", fileKey);
  }

  /**
   * Marks a file as failed processing.
   *
   * @param fileKey The key of the failed file
   * @param fileSize Size of the file in bytes
   * @param errorMessage Error message describing the failure
   */
  public void markFileAsFailed(String fileKey, long fileSize, String errorMessage) {
    logger.debug("Marking file as failed: {}", fileKey);

    ProcessedFile processedFile =
        ProcessedFile.builder()
            .fileName(fileKey)
            .filePath(fileKey)
            .fileSize(fileSize)
            .processedAt(LocalDateTime.now())
            .status(ProcessingStatus.FAILED)
            .errorMessage(errorMessage)
            .build();

    fileTrackingService.markFileAsProcessed(processedFile);
    logger.warn("Marked file as failed: {} - {}", fileKey, errorMessage);
  }

  /**
   * Gets the size of an S3Object.
   *
   * @param s3Object The S3Object
   * @return File size in bytes
   */
  public long getFileSize(S3Object s3Object) {
    return s3Object.size();
  }

  /**
   * Validates if a file is a valid JSON Lines file based on its extension.
   *
   * @param fileName The file name to validate
   * @return true if the file is a .jl file, false otherwise
   */
  public boolean isValidJsonLFile(String fileName) {
    if (fileName == null || fileName.trim().isEmpty()) {
      return false;
    }
    return fileName.toLowerCase().endsWith(JSONL_EXTENSION);
  }

  /**
   * Validates if an S3Object is a valid JSON Lines file.
   *
   * @param s3Object The S3Object to validate
   * @return true if the file is a .jl file, false otherwise
   */
  private boolean isValidJsonLFile(S3Object s3Object) {
    return isValidJsonLFile(s3Object.key());
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

  /**
   * Gets the total size of all files in the bucket with the configured prefix
   *
   * @return Total size in bytes
   */
  public long getTotalSize() {
    return listFiles().stream().mapToLong(S3FileMetadata::getSize).sum();
  }
}
