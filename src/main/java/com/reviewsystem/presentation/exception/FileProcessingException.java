package com.reviewsystem.presentation.exception;

/**
 * Exception thrown when file processing operations fail. This includes S3 operations, file parsing,
 * and data validation failures.
 */
public class FileProcessingException extends RuntimeException {

  /**
   * Constructs a new FileProcessingException with the specified detail message.
   *
   * @param message the detail message
   */
  public FileProcessingException(String message) {
    super(message);
  }

  /**
   * Constructs a new FileProcessingException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public FileProcessingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new FileProcessingException with the specified cause.
   *
   * @param cause the cause of the exception
   */
  public FileProcessingException(Throwable cause) {
    super(cause);
  }

  /**
   * Constructs a new FileProcessingException with the specified detail message, cause, suppression
   * enabled or disabled, and writable stack trace enabled or disabled.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   * @param enableSuppression whether or not suppression is enabled
   * @param writableStackTrace whether or not the stack trace should be writable
   */
  public FileProcessingException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
