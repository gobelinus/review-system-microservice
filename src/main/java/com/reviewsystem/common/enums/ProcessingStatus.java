package com.reviewsystem.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Enumeration representing the processing status of files. */
@Getter
@RequiredArgsConstructor
public enum ProcessingStatus {

  /** File processing has not started yet. */
  PENDING("File is queued for processing"),

  /** File processing is currently in progress. */
  IN_PROGRESS("File processing is currently running"),

  /** File processing completed successfully. */
  COMPLETED("File processing completed successfully"),

  /** File processing failed due to an error. */
  FAILED("File processing failed"),

  /** File processing was cancelled or interrupted. */
  CANCELLED("File processing was cancelled"),

  /** File processing has not started yet and process is IDLE. */
  IDLE("Processor is waiting for next file"),

  /** File processing was skipped (e.g., already processed). */
  SKIPPED("File processing was skipped");

  private final String description;

  /**
   * Checks if the status represents a terminal state (processing is finished).
   *
   * @return true if the status is terminal, false otherwise
   */
  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == CANCELLED || this == SKIPPED;
  }

  /**
   * Checks if the status represents an active processing state.
   *
   * @return true if processing is active, false otherwise
   */
  public boolean isActive() {
    return this == IN_PROGRESS;
  }

  /**
   * Checks if the status represents a successful completion.
   *
   * @return true if processing was successful, false otherwise
   */
  public boolean isSuccessful() {
    return this == COMPLETED;
  }

  /**
   * Checks if the status represents a failure state.
   *
   * @return true if processing failed, false otherwise
   */
  public boolean isFailed() {
    return this == FAILED;
  }
}
