package com.reviewsystem.domain.entity;

import static org.assertj.core.api.Assertions.*;

import com.reviewsystem.common.enums.ProcessingStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProcessedFile Entity Tests")
class ProcessedFileTest {

  private Validator validator;
  private ProcessedFile validProcessedFile;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();

    validProcessedFile =
        ProcessedFile.builder()
            .s3Key("reviews/agoda/2024/01/15/reviews_20240115.jl")
            .etag("d41d8cd98f00b204e9800998ecf8427e")
            .fileSize(1024L)
            .lastModifiedDate(LocalDateTime.now())
            .processingStatus(ProcessingStatus.PENDING)
            .provider("agoda")
            .build();
  }

  @Nested
  @DisplayName("Entity Validation Tests")
  class EntityValidationTests {

    @Test
    @DisplayName("Should pass validation with valid ProcessedFile")
    void shouldPassValidationWithValidProcessedFile() {
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(validProcessedFile);
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when s3Key is null")
    void shouldFailValidationWhenS3KeyIsNull() {
      ProcessedFile file = validProcessedFile.toBuilder().s3Key(null).build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("S3 key cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when s3Key is blank")
    void shouldFailValidationWhenS3KeyIsBlank() {
      ProcessedFile file = validProcessedFile.toBuilder().s3Key("").build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("S3 key cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when s3Key exceeds maximum length")
    void shouldFailValidationWhenS3KeyExceedsMaxLength() {
      String longS3Key = "a".repeat(1025);
      ProcessedFile file = validProcessedFile.toBuilder().s3Key(longS3Key).build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("S3 key cannot exceed 1024 characters");
    }

    @Test
    @DisplayName("Should fail validation when etag is null")
    void shouldFailValidationWhenEtagIsNull() {
      ProcessedFile file = validProcessedFile.toBuilder().etag(null).build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("ETag cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when etag is blank")
    void shouldFailValidationWhenEtagIsBlank() {
      ProcessedFile file = validProcessedFile.toBuilder().etag("   ").build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("ETag cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when etag exceeds maximum length")
    void shouldFailValidationWhenEtagExceedsMaxLength() {
      String longEtag = "a".repeat(256);
      ProcessedFile file = validProcessedFile.toBuilder().etag(longEtag).build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("ETag cannot exceed 255 characters");
    }

    @Test
    @DisplayName("Should fail validation when fileSize is null")
    void shouldFailValidationWhenFileSizeIsNull() {
      ProcessedFile file = validProcessedFile.toBuilder().fileSize(null).build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("File size cannot be null");

      // Assert that other fields are NOT null and retain their original values
      assertThat(file.getS3Key()).isEqualTo(validProcessedFile.getS3Key());
      assertThat(file.getEtag()).isEqualTo(validProcessedFile.getEtag());
      assertThat(file.getLastModifiedDate()).isEqualTo(validProcessedFile.getLastModifiedDate());
      assertThat(file.getProcessingStatus()).isEqualTo(validProcessedFile.getProcessingStatus());
      assertThat(file.getProvider()).isEqualTo(validProcessedFile.getProvider());
    }

    @Test
    @DisplayName("Should fail validation when fileSize is zero or negative")
    void shouldFailValidationWhenFileSizeIsZeroOrNegative() {
      ProcessedFile file1 = validProcessedFile.toBuilder().fileSize(0L).build();
      ProcessedFile file2 = validProcessedFile.toBuilder().fileSize(-1L).build();

      Set<ConstraintViolation<ProcessedFile>> violations1 = validator.validate(file1);
      Set<ConstraintViolation<ProcessedFile>> violations2 = validator.validate(file2);

      assertThat(violations1).hasSize(1);
      assertThat(violations1.iterator().next().getMessage())
          .isEqualTo("File size must be positive");
      assertThat(violations2).hasSize(1);
      assertThat(violations2.iterator().next().getMessage())
          .isEqualTo("File size must be positive");
    }

    @Test
    @DisplayName("Should fail validation when processingStatus is null")
    void shouldFailValidationWhenProcessingStatusIsNull() {
      ProcessedFile file = validProcessedFile.toBuilder().processingStatus(null).build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Processing status cannot be null");
    }

    @Test
    @DisplayName("Should not fail validation when provider is null")
    void shouldNotFailValidationWhenproviderIsNull() {
      ProcessedFile file = validProcessedFile.toBuilder().provider(null).build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(0);
      // assertThat(violations.iterator().next().getMessage()).isEqualTo("provider cannot be null");
    }

    @Test
    @DisplayName("Should fail validation when provider exceeds maximum length")
    void shouldFailValidationWhenproviderExceedsMaxLength() {
      String longprovider = "a".repeat(51);
      ProcessedFile file = validProcessedFile.toBuilder().provider(longprovider).build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("provider cannot exceed 50 characters");
    }

    @Test
    @DisplayName("Should pass validation with maximum allowed field lengths")
    void shouldPassValidationWithMaximumAllowedFieldLengths() {
      ProcessedFile file =
          validProcessedFile.toBuilder()
              .s3Key("a".repeat(1024))
              .etag("a".repeat(255))
              .provider("a".repeat(50))
              .errorMessage("a".repeat(2000))
              .build();

      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(file);
      assertThat(violations).isEmpty();
    }
  }

  @Nested
  @DisplayName("Duplicate Prevention Logic Tests")
  class DuplicatePreventionTests {

    @Test
    @DisplayName("Should identify duplicate files with same s3Key and etag")
    void shouldIdentifyDuplicateFilesWithSameS3KeyAndEtag() {
      String s3Key = "reviews/agoda/2024/01/15/reviews_20240115.jl";
      String etag = "d41d8cd98f00b204e9800998ecf8427e";

      ProcessedFile file = validProcessedFile.builder().s3Key(s3Key).etag(etag).build();

      assertThat(file.isDuplicateOf(s3Key, etag)).isTrue();
    }

    @Test
    @DisplayName("Should not identify as duplicate when s3Key differs")
    void shouldNotIdentifyAsDuplicateWhenS3KeyDiffers() {
      String originalS3Key = "reviews/agoda/2024/01/15/reviews_20240115.jl";
      String differentS3Key = "reviews/agoda/2024/01/16/reviews_20240116.jl";
      String etag = "d41d8cd98f00b204e9800998ecf8427e";

      ProcessedFile file = validProcessedFile.builder().s3Key(originalS3Key).etag(etag).build();

      assertThat(file.isDuplicateOf(differentS3Key, etag)).isFalse();
    }

    @Test
    @DisplayName("Should not identify as duplicate when etag differs")
    void shouldNotIdentifyAsDuplicateWhenEtagDiffers() {
      String s3Key = "reviews/agoda/2024/01/15/reviews_20240115.jl";
      String originalEtag = "d41d8cd98f00b204e9800998ecf8427e";
      String differentEtag = "098f6bcd4621d373cade4e832627b4f6";

      ProcessedFile file = validProcessedFile.builder().s3Key(s3Key).etag(originalEtag).build();

      assertThat(file.isDuplicateOf(s3Key, differentEtag)).isFalse();
    }

    @Test
    @DisplayName("Should not identify as duplicate when both s3Key and etag differ")
    void shouldNotIdentifyAsDuplicateWhenBothS3KeyAndEtagDiffer() {
      String originalS3Key = "reviews/agoda/2024/01/15/reviews_20240115.jl";
      String differentS3Key = "reviews/booking/2024/01/15/reviews_20240115.jl";
      String originalEtag = "d41d8cd98f00b204e9800998ecf8427e";
      String differentEtag = "098f6bcd4621d373cade4e832627b4f6";

      ProcessedFile file =
          validProcessedFile.builder().s3Key(originalS3Key).etag(originalEtag).build();

      assertThat(file.isDuplicateOf(differentS3Key, differentEtag)).isFalse();
    }
  }

  @Nested
  @DisplayName("File Metadata Storage Tests")
  class FileMetadataStorageTests {

    @Test
    @DisplayName("Should store file metadata correctly")
    void shouldStoreFileMetadataCorrectly() {
      LocalDateTime lastModified = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

      ProcessedFile file =
          ProcessedFile.builder()
              .s3Key("reviews/agoda/2024/01/15/reviews_20240115.jl")
              .etag("d41d8cd98f00b204e9800998ecf8427e")
              .fileSize(2048L)
              .lastModifiedDate(lastModified)
              .processingStatus(ProcessingStatus.COMPLETED)
              .provider("agoda")
              .recordsProcessed(100)
              .recordsFailed(5)
              .build();

      assertThat(file.getS3Key()).isEqualTo("reviews/agoda/2024/01/15/reviews_20240115.jl");
      assertThat(file.getEtag()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
      assertThat(file.getFileSize()).isEqualTo(2048L);
      assertThat(file.getLastModifiedDate()).isEqualTo(lastModified);
      assertThat(file.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
      assertThat(file.getProvider()).isEqualTo("agoda");
      assertThat(file.getRecordsProcessed()).isEqualTo(100);
      assertThat(file.getRecordsFailed()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should handle null optional metadata gracefully")
    void shouldHandleNullOptionalMetadataGracefully() {
      ProcessedFile file =
          ProcessedFile.builder()
              .s3Key("reviews/agoda/2024/01/15/reviews_20240115.jl")
              .etag("d41d8cd98f00b204e9800998ecf8427e")
              .fileSize(1024L)
              .processingStatus(ProcessingStatus.PENDING)
              .provider("agoda")
              .build();

      assertThat(file.getLastModifiedDate()).isNull();
      assertThat(file.getRecordsProcessed()).isNull();
      assertThat(file.getRecordsFailed()).isNull();
      assertThat(file.getErrorMessage()).isNull();
      assertThat(file.getProcessingStartedAt()).isNull();
      assertThat(file.getProcessingCompletedAt()).isNull();
    }

    @Test
    @DisplayName("Should truncate error message when it exceeds maximum length")
    void shouldTruncateErrorMessageWhenItExceedsMaximumLength() {
      String longErrorMessage = "a".repeat(2500);
      ProcessedFile file = validProcessedFile.builder().build();

      file.markProcessingFailed(longErrorMessage);

      assertThat(file.getErrorMessage()).hasSize(2000);
      assertThat(file.getErrorMessage()).isEqualTo("a".repeat(2000));
    }

    @Test
    @DisplayName("Should preserve error message when within maximum length")
    void shouldPreserveErrorMessageWhenWithinMaximumLength() {
      String errorMessage = "Processing failed due to invalid JSON format";
      ProcessedFile file = validProcessedFile.builder().build();

      file.markProcessingFailed(errorMessage);

      assertThat(file.getErrorMessage()).isEqualTo(errorMessage);
    }
  }

  @Nested
  @DisplayName("Business Logic Methods Tests")
  class BusinessLogicMethodsTests {

    @Test
    @DisplayName("Should correctly identify successfully processed files")
    void shouldCorrectlyIdentifySuccessfullyProcessedFiles() {
      ProcessedFile completedFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.COMPLETED).build();
      ProcessedFile failedFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.FAILED).build();
      ProcessedFile pendingFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.PENDING).build();

      assertThat(completedFile.isSuccessfullyProcessed()).isTrue();
      assertThat(failedFile.isSuccessfullyProcessed()).isFalse();
      assertThat(pendingFile.isSuccessfullyProcessed()).isFalse();
    }

    @Test
    @DisplayName("Should correctly identify failed processing files")
    void shouldCorrectlyIdentifyFailedProcessingFiles() {
      ProcessedFile failedFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.FAILED).build();
      ProcessedFile completedFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.COMPLETED).build();
      ProcessedFile pendingFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.PENDING).build();

      assertThat(failedFile.isProcessingFailed()).isTrue();
      assertThat(completedFile.isProcessingFailed()).isFalse();
      assertThat(pendingFile.isProcessingFailed()).isFalse();
    }

    @Test
    @DisplayName("Should correctly identify files currently being processed")
    void shouldCorrectlyIdentifyFilesCurrentlyBeingProcessed() {
      ProcessedFile processingFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.IN_PROGRESS).build();
      ProcessedFile completedFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.COMPLETED).build();
      ProcessedFile pendingFile =
          validProcessedFile.builder().processingStatus(ProcessingStatus.PENDING).build();

      assertThat(processingFile.isProcessing()).isTrue();
      assertThat(completedFile.isProcessing()).isFalse();
      assertThat(pendingFile.isProcessing()).isFalse();
    }

    @Test
    @DisplayName("Should mark processing as started correctly")
    void shouldMarkProcessingAsStartedCorrectly() {
      ProcessedFile file = validProcessedFile.builder().build();
      LocalDateTime beforeMark = LocalDateTime.now().minusSeconds(1);

      file.markProcessingStarted();

      LocalDateTime afterMark = LocalDateTime.now().plusSeconds(1);

      assertThat(file.getProcessingStatus()).isEqualTo(ProcessingStatus.IN_PROGRESS);
      assertThat(file.getProcessingStartedAt()).isBetween(beforeMark, afterMark);
      assertThat(file.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should mark processing as completed correctly")
    void shouldMarkProcessingAsCompletedCorrectly() {
      ProcessedFile file = validProcessedFile.builder().build();
      LocalDateTime beforeMark = LocalDateTime.now().minusSeconds(1);

      file.markProcessingCompleted(150, 10);

      LocalDateTime afterMark = LocalDateTime.now().plusSeconds(1);

      assertThat(file.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
      assertThat(file.getProcessingCompletedAt()).isBetween(beforeMark, afterMark);
      assertThat(file.getRecordsProcessed()).isEqualTo(150);
      assertThat(file.getRecordsFailed()).isEqualTo(10);
      assertThat(file.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should mark processing as failed correctly")
    void shouldMarkProcessingAsFailedCorrectly() {
      ProcessedFile file = validProcessedFile.builder().build();
      String errorMessage = "Connection timeout to database";
      LocalDateTime beforeMark = LocalDateTime.now().minusSeconds(1);

      file.markProcessingFailed(errorMessage);

      LocalDateTime afterMark = LocalDateTime.now().plusSeconds(1);

      assertThat(file.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
      assertThat(file.getProcessingCompletedAt()).isBetween(beforeMark, afterMark);
      assertThat(file.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("Should calculate processing duration correctly when processing is complete")
    void shouldCalculateProcessingDurationCorrectlyWhenProcessingIsComplete() {
      ProcessedFile file = validProcessedFile.builder().build();
      LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
      LocalDateTime endTime = LocalDateTime.now();

      file.setProcessingStartedAt(startTime);
      file.setProcessingCompletedAt(endTime);

      Long duration = file.getProcessingDurationMillis();

      assertThat(duration).isNotNull();
      assertThat(duration).isGreaterThan(0);
      // Allow for some variation in timing
      assertThat(duration).isBetween(290000L, 310000L); // ~5 minutes ± 10 seconds
    }

    @Test
    @DisplayName(
        "Should calculate processing duration from start time to now when processing is ongoing")
    void shouldCalculateProcessingDurationFromStartTimeToNowWhenProcessingIsOngoing() {
      ProcessedFile file = validProcessedFile.builder().build();
      LocalDateTime startTime = LocalDateTime.now().minusMinutes(2);

      file.setProcessingStartedAt(startTime);
      // No completion time set

      Long duration = file.getProcessingDurationMillis();

      assertThat(duration).isNotNull();
      assertThat(duration).isGreaterThan(0);
      // Should be approximately 2 minutes
      assertThat(duration).isBetween(110000L, 130000L); // ~2 minutes ± 10 seconds
    }

    @Test
    @DisplayName("Should return null duration when processing has not started")
    void shouldReturnNullDurationWhenProcessingHasNotStarted() {
      ProcessedFile file = validProcessedFile.builder().build();
      // No start time set

      Long duration = file.getProcessingDurationMillis();

      assertThat(duration).isNull();
    }
  }

  @Nested
  @DisplayName("Builder Pattern Tests")
  class BuilderPatternTests {

    @Test
    @DisplayName("Should create ProcessedFile using builder with all fields")
    void shouldCreateProcessedFileUsingBuilderWithAllFields() {
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime lastModified = now.minusHours(1);

      ProcessedFile file =
          ProcessedFile.builder()
              .id(1L)
              .s3Key("reviews/agoda/2024/01/15/reviews_20240115.jl")
              .etag("d41d8cd98f00b204e9800998ecf8427e")
              .fileSize(2048L)
              .lastModifiedDate(lastModified)
              .processingStatus(ProcessingStatus.COMPLETED)
              .recordsProcessed(100)
              .recordsFailed(5)
              .errorMessage(null)
              .provider("agoda")
              .createdAt(now)
              .updatedAt(now)
              .processingStartedAt(now.minusMinutes(30))
              .processingCompletedAt(now.minusMinutes(10))
              .build();

      assertThat(file.getId()).isEqualTo(1L);
      assertThat(file.getS3Key()).isEqualTo("reviews/agoda/2024/01/15/reviews_20240115.jl");
      assertThat(file.getEtag()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
      assertThat(file.getFileSize()).isEqualTo(2048L);
      assertThat(file.getLastModifiedDate()).isEqualTo(lastModified);
      assertThat(file.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
      assertThat(file.getRecordsProcessed()).isEqualTo(100);
      assertThat(file.getRecordsFailed()).isEqualTo(5);
      assertThat(file.getProvider()).isEqualTo("agoda");
    }

    @Test
    @DisplayName("Should use default values when not specified in builder")
    void shouldUseDefaultValuesWhenNotSpecifiedInBuilder() {
      ProcessedFile file =
          ProcessedFile.builder()
              .s3Key("reviews/agoda/2024/01/15/reviews_20240115.jl")
              .etag("d41d8cd98f00b204e9800998ecf8427e")
              .fileSize(1024L)
              .provider("agoda")
              .build();

      assertThat(file.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
      assertThat(file.getRecordsProcessed()).isNull();
      assertThat(file.getRecordsFailed()).isNull();
      assertThat(file.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should support builder for creating modified copies")
    void shouldSupportbuilderForCreatingModifiedCopies() {
      ProcessedFile original = validProcessedFile.builder().build();

      ProcessedFile modified =
          original
              .builder()
              .processingStatus(ProcessingStatus.COMPLETED)
              .recordsProcessed(200)
              .recordsFailed(10)
              .build();

      // Original should remain unchanged
      assertThat(original.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
      assertThat(original.getRecordsProcessed()).isNull();
      assertThat(original.getRecordsFailed()).isNull();

      // Modified should have new values
      assertThat(modified.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
      assertThat(modified.getRecordsProcessed()).isEqualTo(200);
      assertThat(modified.getRecordsFailed()).isEqualTo(10);

      // Common fields should be same
      assertThat(modified.getS3Key()).isEqualTo(original.getS3Key());
      assertThat(modified.getEtag()).isEqualTo(original.getEtag());
      assertThat(modified.getProvider()).isEqualTo(original.getProvider());
    }
  }
}
