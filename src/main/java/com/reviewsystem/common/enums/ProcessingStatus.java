package com.reviewsystem.common.enums;

/**
 * Stub for ProcessingStatus enum Represents the various states of file processing in the review
 * system.
 *
 * <p>This enum should represent: - Initial state when file is discovered - Processing in progress
 * state - Successful completion state - Various failure states - Retry states - Skipped/ignored
 * states
 *
 * <p>TODO: Implement the following: - All necessary status values - Description/display name for
 * each status - Methods to check if status is terminal (finished) - Methods to check if status
 * allows retry - Methods to get next possible statuses - Integration with database (JPA converter
 * if needed)
 */
public enum ProcessingStatus {

// TODO: Add PENDING status for files waiting to be processed

// TODO: Add IN_PROGRESS status for files currently being processed

// TODO: Add COMPLETED status for successfully processed files

// TODO: Add FAILED status for files that failed processing

// TODO: Add RETRY status for files that are being retried

// TODO: Add SKIPPED status for files that were intentionally skipped

// TODO: Add DUPLICATE status for files that were already processed

// TODO: Add CORRUPTED status for files that are corrupted/unreadable

// TODO: Add description field for each enum value

// TODO: Add constructor to initialize description

// TODO: Add getter method for description

// TODO: Add isTerminal() method to check if status is final

// TODO: Add isRetryable() method to check if status allows retry

// TODO: Add canTransitionTo() method to validate status transitions

// TODO: Add getDisplayName() method for UI representation
}
