package com.reviewsystem.common.constants;

/** Application-wide constants. */
public final class ApplicationConstants {

  private ApplicationConstants() {
    // Prevent instantiation
  }

  // File Processing Constants
  public static final String JSONL_FILE_EXTENSION = ".jl";
  public static final String JSON_FILE_EXTENSION = ".json";
  public static final int DEFAULT_BATCH_SIZE = 1000;
  public static final int MAX_RETRY_ATTEMPTS = 3;
  public static final int RETRY_DELAY_SECONDS = 2;

  // S3 Constants
  public static final String S3_REVIEWS_PREFIX = "reviews/";
  public static final int S3_MAX_KEYS_PER_REQUEST = 1000;
  public static final int S3_CONNECTION_TIMEOUT_MS = 5000;
  public static final int S3_SOCKET_TIMEOUT_MS = 30000;

  // Database Constants
  public static final int MAX_STRING_LENGTH = 255;
  public static final int MAX_TEXT_LENGTH = 4000;
  public static final int MAX_FILENAME_LENGTH = 500;
  public static final int MAX_FILEPATH_LENGTH = 1000;

  // Processing Constants
  public static final int MIN_RATING = 1;
  public static final int MAX_RATING = 10;
  public static final int MAX_COMMENT_LENGTH = 5000;
  public static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  // Validation Messages
  public static final String VALIDATION_FILE_KEY_REQUIRED = "File key is required";
  public static final String VALIDATION_FILE_KEY_EMPTY = "File key cannot be empty";
  public static final String VALIDATION_INVALID_RATING = "Rating must be between 1 and 10";
  public static final String VALIDATION_COMMENT_TOO_LONG = "Comment exceeds maximum length";

  // Error Messages
  public static final String ERROR_FILE_NOT_FOUND = "File not found: %s";
  public static final String ERROR_ACCESS_DENIED = "Access denied for file: %s";
  public static final String ERROR_NETWORK_FAILURE = "Network failure while accessing S3";
  public static final String ERROR_INVALID_JSON = "Invalid JSON format in file: %s";
  public static final String ERROR_PROCESSING_FAILED = "Failed to process file: %s";

  // Logging Messages
  public static final String LOG_FILE_PROCESSING_STARTED = "Started processing file: {}";
  public static final String LOG_FILE_PROCESSING_COMPLETED =
      "Completed processing file: {} - Records processed: {}, Records skipped: {}";
  public static final String LOG_FILE_PROCESSING_FAILED = "Failed processing file: {} - Error: {}";
  public static final String LOG_S3_LISTING_FILES =
      "Listing files from S3 bucket: {} with prefix: {}";
  public static final String LOG_S3_DOWNLOAD_STARTED = "Starting download of file: {}";
  public static final String LOG_S3_DOWNLOAD_COMPLETED = "Completed download of file: {}";

  // Scheduling Constants
  public static final String CRON_DAILY_AT_MIDNIGHT = "0 0 0 * * ?";
  public static final String CRON_HOURLY = "0 0 * * * ?";
  public static final String CRON_EVERY_30_MINUTES = "0 */30 * * * ?";

  // Thread Pool Constants
  public static final int CORE_POOL_SIZE = 2;
  public static final int MAX_POOL_SIZE = 10;
  public static final int QUEUE_CAPACITY = 100;
  public static final String THREAD_NAME_PREFIX = "ReviewProcessor-";

  // Cache Constants
  public static final String CACHE_PROCESSED_FILES = "processedFiles";
  public static final String CACHE_providerS = "providers";
  public static final int CACHE_TTL_MINUTES = 60;

  // API Constants
  public static final String API_VERSION = "v1";
  public static final String API_BASE_PATH = "/api/" + API_VERSION;
  public static final String API_REVIEWS_PATH = API_BASE_PATH + "/reviews";
  public static final String API_ADMIN_PATH = API_BASE_PATH + "/admin";
  public static final String API_HEALTH_PATH = "/health";

  // HTTP Headers
  public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
  public static final String HEADER_REQUEST_ID = "X-Request-ID";
  public static final String HEADER_API_VERSION = "X-API-Version";

  // provider Constants
  public static final String provider_AGODA = "AGODA";
  public static final String provider_BOOKING = "BOOKING";
  public static final String provider_EXPEDIA = "EXPEDIA";

  // Date Formats
  public static final String DATE_FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ss";
  public static final String DATE_FORMAT_SIMPLE = "yyyy-MM-dd";
  public static final String DATE_FORMAT_FILENAME = "yyyy-MM-dd-HH-mm-ss";
}
