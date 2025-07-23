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

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Nested
  @DisplayName("Entity Creation Tests")
  class EntityCreationTests {

    @Test
    @DisplayName("Should create ProcessedFile with builder pattern")
    void shouldCreateProcessedFileWithBuilder() {
      // Arrange & Act
      LocalDateTime now = LocalDateTime.now();
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .processedAt(now)
              .recordsProcessed(100)
              .recordsSkipped(5)
              .totalRecords(105)
              .processingDurationMs(5000L)
              .checksum("abc123def456")
              .build();

      // Assert
      assertThat(processedFile.getFileName()).isEqualTo("test-file.jl");
      assertThat(processedFile.getFilePath()).isEqualTo("reviews/2024-01-01/test-file.jl");
      assertThat(processedFile.getFileSize()).isEqualTo(1024L);
      assertThat(processedFile.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
      assertThat(processedFile.getProcessedAt()).isEqualTo(now);
      assertThat(processedFile.getRecordsProcessed()).isEqualTo(100);
      assertThat(processedFile.getRecordsSkipped()).isEqualTo(5);
      assertThat(processedFile.getTotalRecords()).isEqualTo(105);
      assertThat(processedFile.getProcessingDurationMs()).isEqualTo(5000L);
      assertThat(processedFile.getChecksum()).isEqualTo("abc123def456");
    }

    @Test
    @DisplayName("Should create ProcessedFile with default constructor")
    void shouldCreateProcessedFileWithDefaultConstructor() {
      // Act
      ProcessedFile processedFile = new ProcessedFile();

      // Assert
      assertThat(processedFile.getId()).isNull();
      assertThat(processedFile.getFileName()).isNull();
      assertThat(processedFile.getFilePath()).isNull();
      assertThat(processedFile.getFileSize()).isNull();
      assertThat(processedFile.getStatus()).isNull();
      assertThat(processedFile.getProcessedAt()).isNull();
      assertThat(processedFile.getRecordsProcessed()).isNull();
      assertThat(processedFile.getRecordsSkipped()).isNull();
      assertThat(processedFile.getTotalRecords()).isNull();
      assertThat(processedFile.getErrorMessage()).isNull();
      assertThat(processedFile.getProcessingDurationMs()).isNull();
      assertThat(processedFile.getChecksum()).isNull();
      assertThat(processedFile.getCreatedAt()).isNull();
      assertThat(processedFile.getUpdatedAt()).isNull();
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should pass validation with valid data")
    void shouldPassValidationWithValidData() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(100)
              .recordsSkipped(5)
              .totalRecords(105)
              .processingDurationMs(5000L)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when fileName is null")
    void shouldFailValidationWhenFileNameIsNull() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName(null)
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("File name cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when fileName is blank")
    void shouldFailValidationWhenFileNameIsBlank() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("   ")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("File name cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when filePath is null")
    void shouldFailValidationWhenFilePathIsNull() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath(null)
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("File path cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when fileSize is null")
    void shouldFailValidationWhenFileSizeIsNull() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(null)
              .status(ProcessingStatus.COMPLETED)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("File size cannot be null");
    }

    @Test
    @DisplayName("Should fail validation when fileSize is negative")
    void shouldFailValidationWhenFileSizeIsNegative() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(-100L)
              .status(ProcessingStatus.COMPLETED)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("File size must be zero or positive");
    }

    @Test
    @DisplayName("Should fail validation when status is null")
    void shouldFailValidationWhenStatusIsNull() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(null)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Processing status cannot be null");
    }

    @Test
    @DisplayName("Should fail validation when recordsProcessed is negative")
    void shouldFailValidationWhenRecordsProcessedIsNegative() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(-5)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Records processed must be zero or positive");
    }

    @Test
    @DisplayName("Should fail validation when recordsSkipped is negative")
    void shouldFailValidationWhenRecordsSkippedIsNegative() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .recordsSkipped(-3)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Records skipped must be zero or positive");
    }

    @Test
    @DisplayName("Should fail validation when processingDurationMs is negative")
    void shouldFailValidationWhenProcessingDurationIsNegative() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .processingDurationMs(-1000L)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("Processing duration must be zero or positive");
    }
  }

  @Nested
  @DisplayName("Lifecycle Callback Tests")
  class LifecycleCallbackTests {

    @Test
    @DisplayName("Should set processedAt on prePersist when null")
    void shouldSetProcessedAtOnPrePersistWhenNull() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .processedAt(null)
              .build();

      LocalDateTime beforePersist = LocalDateTime.now();

      // Act
      processedFile.prePersist();

      // Assert
      LocalDateTime afterPersist = LocalDateTime.now();
      assertThat(processedFile.getProcessedAt()).isNotNull();
      assertThat(processedFile.getProcessedAt()).isBetween(beforePersist, afterPersist);
    }

    @Test
    @DisplayName("Should not override processedAt on prePersist when already set")
    void shouldNotOverrideProcessedAtOnPrePersistWhenAlreadySet() {
      // Arrange
      LocalDateTime originalTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .processedAt(originalTime)
              .build();

      // Act
      processedFile.prePersist();

      // Assert
      assertThat(processedFile.getProcessedAt()).isEqualTo(originalTime);
    }

    @Test
    @DisplayName("Should calculate totalRecords on prePersist when null")
    void shouldCalculateTotalRecordsOnPrePersistWhenNull() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(100)
              .recordsSkipped(5)
              .totalRecords(null)
              .build();

      // Act
      processedFile.prePersist();

      // Assert
      assertThat(processedFile.getTotalRecords()).isEqualTo(105);
    }

    @Test
    @DisplayName("Should not calculate totalRecords on prePersist when recordsProcessed is null")
    void shouldNotCalculateTotalRecordsOnPrePersistWhenRecordsProcessedIsNull() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(null)
              .recordsSkipped(5)
              .totalRecords(null)
              .build();

      // Act
      processedFile.prePersist();

      // Assert
      assertThat(processedFile.getTotalRecords()).isNull();
    }

    @Test
    @DisplayName("Should calculate totalRecords on preUpdate when null")
    void shouldCalculateTotalRecordsOnPreUpdateWhenNull() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(150)
              .recordsSkipped(10)
              .totalRecords(null)
              .build();

      // Act
      processedFile.preUpdate();

      // Assert
      assertThat(processedFile.getTotalRecords()).isEqualTo(160);
    }

    @Test
    @DisplayName("Should not override totalRecords on preUpdate when already set")
    void shouldNotOverrideTotalRecordsOnPreUpdateWhenAlreadySet() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(100)
              .recordsSkipped(5)
              .totalRecords(200) // Already set to different value
              .build();

      // Act
      processedFile.preUpdate();

      // Assert
      assertThat(processedFile.getTotalRecords()).isEqualTo(200); // Should remain unchanged
    }
  }

  @Nested
  @DisplayName("Utility Methods Tests")
  class UtilityMethodsTests {

    @Test
    @DisplayName("isSuccessful should return true when status is COMPLETED")
    void isSuccessfulShouldReturnTrueWhenStatusIsCompleted() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder().status(ProcessingStatus.COMPLETED).build();

      // Act & Assert
      assertThat(processedFile.isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("isSuccessful should return false when status is FAILED")
    void isSuccessfulShouldReturnFalseWhenStatusIsFailed() {
      // Arrange
      ProcessedFile processedFile = ProcessedFile.builder().status(ProcessingStatus.FAILED).build();

      // Act & Assert
      assertThat(processedFile.isSuccessful()).isFalse();
    }

    @Test
    @DisplayName("isSuccessful should return false when status is IN_PROGRESS")
    void isSuccessfulShouldReturnFalseWhenStatusIsInProgress() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder().status(ProcessingStatus.IN_PROGRESS).build();

      // Act & Assert
      assertThat(processedFile.isSuccessful()).isFalse();
    }

    @Test
    @DisplayName("isFailed should return true when status is FAILED")
    void isFailedShouldReturnTrueWhenStatusIsFailed() {
      // Arrange
      ProcessedFile processedFile = ProcessedFile.builder().status(ProcessingStatus.FAILED).build();

      // Act & Assert
      assertThat(processedFile.isFailed()).isTrue();
    }

    @Test
    @DisplayName("isFailed should return false when status is COMPLETED")
    void isFailedShouldReturnFalseWhenStatusIsCompleted() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder().status(ProcessingStatus.COMPLETED).build();

      // Act & Assert
      assertThat(processedFile.isFailed()).isFalse();
    }

    @Test
    @DisplayName("isInProgress should return true when status is IN_PROGRESS")
    void isInProgressShouldReturnTrueWhenStatusIsInProgress() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder().status(ProcessingStatus.IN_PROGRESS).build();

      // Act & Assert
      assertThat(processedFile.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("isInProgress should return false when status is COMPLETED")
    void isInProgressShouldReturnFalseWhenStatusIsCompleted() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder().status(ProcessingStatus.COMPLETED).build();

      // Act & Assert
      assertThat(processedFile.isInProgress()).isFalse();
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("Should be equal when all fields are same")
    void shouldBeEqualWhenAllFieldsAreSame() {
      // Arrange
      LocalDateTime now = LocalDateTime.now();
      ProcessedFile file1 =
          ProcessedFile.builder()
              .id(1L)
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .processedAt(now)
              .recordsProcessed(100)
              .recordsSkipped(5)
              .build();

      ProcessedFile file2 =
          ProcessedFile.builder()
              .id(1L)
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .processedAt(now)
              .recordsProcessed(100)
              .recordsSkipped(5)
              .build();

      // Act & Assert
      assertThat(file1).isEqualTo(file2);
      assertThat(file1.hashCode()).isEqualTo(file2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when fileName is different")
    void shouldNotBeEqualWhenFileNameIsDifferent() {
      // Arrange
      ProcessedFile file1 =
          ProcessedFile.builder()
              .fileName("test-file1.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .build();

      ProcessedFile file2 =
          ProcessedFile.builder()
              .fileName("test-file2.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .build();

      // Act & Assert
      assertThat(file1).isNotEqualTo(file2);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle zero file size")
    void shouldHandleZeroFileSize() {
      // Arrange & Act
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("empty-file.jl")
              .filePath("reviews/2024-01-01/empty-file.jl")
              .fileSize(0L)
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(0)
              .recordsSkipped(0)
              .build();

      // Assert
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);
      assertThat(violations).isEmpty();
      assertThat(processedFile.getFileSize()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should handle zero processing duration")
    void shouldHandleZeroProcessingDuration() {
      // Arrange & Act
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("fast-file.jl")
              .filePath("reviews/2024-01-01/fast-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .processingDurationMs(0L)
              .build();

      // Assert
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);
      assertThat(violations).isEmpty();
      assertThat(processedFile.getProcessingDurationMs()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should handle very long file paths")
    void shouldHandleVeryLongFilePaths() {
      // Arrange
      String longPath = "reviews/" + "very-long-path-".repeat(50) + "file.jl";

      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("file.jl")
              .filePath(longPath)
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .build();

      // Act
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);

      // Assert - Should pass validation as long as it's under the column length limit
      if (longPath.length() <= 1000) {
        assertThat(violations).isEmpty();
      }
      assertThat(processedFile.getFilePath()).isEqualTo(longPath);
    }

    @Test
    @DisplayName("Should handle null optional fields gracefully")
    void shouldHandleNullOptionalFieldsGracefully() {
      // Arrange & Act
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName("test-file.jl")
              .filePath("reviews/2024-01-01/test-file.jl")
              .fileSize(1024L)
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(null)
              .recordsSkipped(null)
              .totalRecords(null)
              .errorMessage(null)
              .processingDurationMs(null)
              .checksum(null)
              .build();

      // Assert
      Set<ConstraintViolation<ProcessedFile>> violations = validator.validate(processedFile);
      assertThat(violations).isEmpty();

      // Verify lifecycle callback doesn't crash with null values
      assertThatCode(
              () -> {
                processedFile.prePersist();
                processedFile.preUpdate();
              })
          .doesNotThrowAnyException();
    }
  }
}
