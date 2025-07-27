package com.reviewsystem.domain.repository;

import static org.assertj.core.api.Assertions.*;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.ProcessedFile;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test-postgres")
@DisplayName("ProcessedFile Repository Tests")
class ProcessedFileRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private ProcessedFileRepository processedFileRepository;

  private ProcessedFile sampleFile1;
  private ProcessedFile sampleFile2;
  private ProcessedFile sampleFile3;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();

    sampleFile1 =
        ProcessedFile.builder()
            .s3Key("reviews/agoda/2024/01/15/reviews_20240115.jl")
            .etag("etag1")
            .fileSize(1024L)
            .lastModifiedDate(now.minusHours(2))
            .processingStatus(ProcessingStatus.COMPLETED)
            .recordsProcessed(100)
            .recordsFailed(5)
            .provider("agoda")
            .build();

    sampleFile2 =
        ProcessedFile.builder()
            .s3Key("reviews/booking/2024/01/15/reviews_20240115.jl")
            .etag("etag2")
            .fileSize(2048L)
            .lastModifiedDate(now.minusHours(1))
            .processingStatus(ProcessingStatus.FAILED)
            .errorMessage("Connection timeout")
            .provider(ProviderType.BOOKING.getDisplayName())
            .build();

    sampleFile3 =
        ProcessedFile.builder()
            .s3Key("reviews/expedia/2024/01/15/reviews_20240115.jl")
            .etag("etag3")
            .fileSize(4096L)
            .lastModifiedDate(now.minusMinutes(30))
            .processingStatus(ProcessingStatus.IN_PROGRESS)
            .provider("expedia")
            .build();
  }

  @Nested
  @DisplayName("Basic CRUD Operations")
  class BasicCrudOperationsTests {

    @Test
    @DisplayName("Should save and retrieve ProcessedFile")
    void shouldSaveAndRetrieveProcessedFile() {
      ProcessedFile saved = processedFileRepository.save(sampleFile1);

      assertThat(saved.getId()).isNotNull();

      Optional<ProcessedFile> retrieved = processedFileRepository.findById(saved.getId());

      assertThat(retrieved).isPresent();
      assertThat(retrieved.get().getS3Key()).isEqualTo(sampleFile1.getS3Key());
      assertThat(retrieved.get().getEtag()).isEqualTo(sampleFile1.getEtag());
      assertThat(retrieved.get().getProvider()).isEqualTo(sampleFile1.getProvider());
    }

    @Test
    @DisplayName("Should return empty when finding non-existent file by ID")
    void shouldReturnEmptyWhenFindingNonExistentFileById() {
      Optional<ProcessedFile> result = processedFileRepository.findById(999L);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should delete ProcessedFile by ID")
    void shouldDeleteProcessedFileById() {
      ProcessedFile saved = processedFileRepository.save(sampleFile1);
      Long id = saved.getId();

      processedFileRepository.deleteById(id);

      Optional<ProcessedFile> result = processedFileRepository.findById(id);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should find all ProcessedFiles")
    void shouldFindAllProcessedFiles() {
      processedFileRepository.save(sampleFile1);
      processedFileRepository.save(sampleFile2);
      processedFileRepository.save(sampleFile3);

      List<ProcessedFile> allFiles = processedFileRepository.findAll();

      assertThat(allFiles).hasSize(3);
      assertThat(allFiles)
          .extracting(ProcessedFile::getProvider)
          .containsExactlyInAnyOrder("agoda", "booking", "expedia");
    }

    @Test
    @DisplayName("Should delete all ProcessedFiles")
    void shouldDeleteAllProcessedFiles() {
      processedFileRepository.save(sampleFile1);
      processedFileRepository.save(sampleFile2);

      processedFileRepository.deleteAll();

      List<ProcessedFile> allFiles = processedFileRepository.findAll();
      assertThat(allFiles).isEmpty();
    }
  }

  @Nested
  @DisplayName("Find by S3 Key and ETag Tests")
  class FindByS3KeyAndETagTests {

    @Test
    @DisplayName("Should find file by S3 key and ETag")
    void shouldFindFileByS3KeyAndETag() {
      processedFileRepository.save(sampleFile1);

      Optional<ProcessedFile> result =
          processedFileRepository.findByS3KeyAndEtag(sampleFile1.getS3Key(), sampleFile1.getEtag());

      assertThat(result).isPresent();
      assertThat(result.get().getS3Key()).isEqualTo(sampleFile1.getS3Key());
      assertThat(result.get().getEtag()).isEqualTo(sampleFile1.getEtag());
    }

    @Test
    @DisplayName("Should return empty when S3 key and ETag combination does not exist")
    void shouldReturnEmptyWhenS3KeyAndETagCombinationDoesNotExist() {
      processedFileRepository.save(sampleFile1);

      Optional<ProcessedFile> result =
          processedFileRepository.findByS3KeyAndEtag("non-existent-key", "non-existent-etag");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when S3 key exists but ETag is different")
    void shouldReturnEmptyWhenS3KeyExistsButETagIsDifferent() {
      processedFileRepository.save(sampleFile1);

      Optional<ProcessedFile> result =
          processedFileRepository.findByS3KeyAndEtag(sampleFile1.getS3Key(), "different-etag");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should check existence by S3 key and ETag")
    void shouldCheckExistenceByS3KeyAndETag() {
      processedFileRepository.save(sampleFile1);

      boolean exists =
          processedFileRepository.existsByS3KeyAndEtag(
              sampleFile1.getS3Key(), sampleFile1.getEtag());
      boolean notExists =
          processedFileRepository.existsByS3KeyAndEtag("non-existent-key", "non-existent-etag");

      assertThat(exists).isTrue();
      assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should find file by S3 key only")
    void shouldFindFileByS3KeyOnly() {
      processedFileRepository.save(sampleFile1);

      Optional<ProcessedFile> result = processedFileRepository.findByS3Key(sampleFile1.getS3Key());

      assertThat(result).isPresent();
      assertThat(result.get().getS3Key()).isEqualTo(sampleFile1.getS3Key());
    }
  }

  @Nested
  @DisplayName("Find by Processing Status Tests")
  class FindByProcessingStatusTests {

    @Test
    @DisplayName("Should find files by processing status")
    void shouldFindFilesByProcessingStatus() {
      processedFileRepository.save(sampleFile1); // COMPLETED
      processedFileRepository.save(sampleFile2); // FAILED
      processedFileRepository.save(sampleFile3); // PROCESSING

      List<ProcessedFile> completedFiles =
          processedFileRepository.findByProcessingStatus(ProcessingStatus.COMPLETED);
      List<ProcessedFile> failedFiles =
          processedFileRepository.findByProcessingStatus(ProcessingStatus.FAILED);
      List<ProcessedFile> processingFiles =
          processedFileRepository.findByProcessingStatus(ProcessingStatus.IN_PROGRESS);

      assertThat(completedFiles).hasSize(1);
      assertThat(completedFiles.get(0).getProvider()).isEqualTo("agoda");

      assertThat(failedFiles).hasSize(1);
      assertThat(failedFiles.get(0).getProvider()).isEqualTo("booking");

      assertThat(processingFiles).hasSize(1);
      assertThat(processingFiles.get(0).getProvider()).isEqualTo("expedia");
    }

    @Test
    @DisplayName("Should return empty list when no files match processing status")
    void shouldReturnEmptyListWhenNoFilesMatchProcessingStatus() {
      processedFileRepository.save(sampleFile1); // COMPLETED

      List<ProcessedFile> pendingFiles =
          processedFileRepository.findByProcessingStatus(ProcessingStatus.PENDING);

      assertThat(pendingFiles).isEmpty();
    }

    @Test
    @DisplayName("Should find files by provider and processing status")
    void shouldFindFilesByproviderAndProcessingStatus() {
      ProcessedFile agodaCompleted = sampleFile1.builder().build();
      ProcessedFile agodaFailed =
          ProcessedFile.builder()
              .s3Key("reviews/agoda/2024/01/16/reviews_20240116.jl")
              .etag("etag4")
              .fileSize(1024L)
              .processingStatus(ProcessingStatus.FAILED)
              .provider("agoda")
              .build();

      processedFileRepository.save(agodaCompleted);
      processedFileRepository.save(agodaFailed);
      processedFileRepository.save(sampleFile2); // booking, failed

      List<ProcessedFile> agodaCompletedFiles =
          processedFileRepository.findByProviderAndProcessingStatus(
              "agoda", ProcessingStatus.COMPLETED);
      List<ProcessedFile> agodaFailedFiles =
          processedFileRepository.findByProviderAndProcessingStatus(
              "agoda", ProcessingStatus.FAILED);

      assertThat(agodaCompletedFiles).hasSize(1);
      assertThat(agodaCompletedFiles.get(0).getS3Key()).contains("20240115");

      assertThat(agodaFailedFiles).hasSize(1);
      assertThat(agodaFailedFiles.get(0).getS3Key()).contains("20240116");
    }

    @Test
    @DisplayName("Should count files by processing status")
    void shouldCountFilesByProcessingStatus() {
      processedFileRepository.save(sampleFile1); // COMPLETED
      processedFileRepository.save(sampleFile2); // FAILED
      processedFileRepository.save(sampleFile3); // PROCESSING

      long completedCount =
          processedFileRepository.countByProcessingStatus(ProcessingStatus.COMPLETED);
      long failedCount = processedFileRepository.countByProcessingStatus(ProcessingStatus.FAILED);
      long processingCount =
          processedFileRepository.countByProcessingStatus(ProcessingStatus.IN_PROGRESS);
      long pendingCount = processedFileRepository.countByProcessingStatus(ProcessingStatus.PENDING);

      assertThat(completedCount).isEqualTo(1);
      assertThat(failedCount).isEqualTo(1);
      assertThat(processingCount).isEqualTo(1);
      assertThat(pendingCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should count files by provider and processing status")
    void shouldCountFilesByproviderAndProcessingStatus() {
      ProcessedFile agodaCompleted = sampleFile1.builder().build();
      ProcessedFile agodaFailed =
          ProcessedFile.builder()
              .s3Key("reviews/agoda/2024/01/16/reviews_20240116.jl")
              .etag("etag4")
              .fileSize(1024L)
              .processingStatus(ProcessingStatus.FAILED)
              .provider("agoda")
              .build();

      processedFileRepository.save(agodaCompleted);
      processedFileRepository.save(agodaFailed);
      processedFileRepository.save(sampleFile2); // booking, failed

      long agodaCompletedCount =
          processedFileRepository.countByProviderAndProcessingStatus(
              "agoda", ProcessingStatus.COMPLETED);
      long agodaFailedCount =
          processedFileRepository.countByProviderAndProcessingStatus(
              "agoda", ProcessingStatus.FAILED);
      long bookingFailedCount =
          processedFileRepository.countByProviderAndProcessingStatus(
              "booking", ProcessingStatus.FAILED);

      assertThat(agodaCompletedCount).isEqualTo(1);
      assertThat(agodaFailedCount).isEqualTo(1);
      assertThat(bookingFailedCount).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Date-based Query Tests")
  class DateBasedQueryTests {

    @Test
    @DisplayName("Should find files created after specific date")
    void shouldFindFilesCreatedAfterSpecificDate() {
      LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);

      // Force creation times by persisting and flushing
      sampleFile1.setCreatedAt(LocalDateTime.now().minusHours(2)); // Before cutoff
      sampleFile2.setCreatedAt(LocalDateTime.now().minusMinutes(30)); // After cutoff
      sampleFile3.setCreatedAt(LocalDateTime.now().minusMinutes(15)); // After cutoff

      entityManager.persistAndFlush(sampleFile1);
      entityManager.persistAndFlush(sampleFile2);
      entityManager.persistAndFlush(sampleFile3);

      List<ProcessedFile> recentFiles = processedFileRepository.findByCreatedAtAfter(cutoffTime);

      assertThat(recentFiles).hasSize(2);
      assertThat(recentFiles)
          .extracting(ProcessedFile::getProvider)
          .containsExactlyInAnyOrder("booking", "expedia");
    }

    @Test
    @DisplayName("Should find old processed files for cleanup")
    void shouldFindOldProcessedFilesForCleanup() {
      LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

      // Create old files with terminal statuses
      sampleFile1.setCreatedAt(LocalDateTime.now().minusDays(35));
      sampleFile1.setProcessingStatus(ProcessingStatus.COMPLETED);

      sampleFile2.setCreatedAt(LocalDateTime.now().minusDays(32));
      sampleFile2.setProcessingStatus(ProcessingStatus.FAILED);

      // Create recent file - should not be included
      sampleFile3.setCreatedAt(LocalDateTime.now().minusDays(15));
      sampleFile3.setProcessingStatus(ProcessingStatus.COMPLETED);

      entityManager.persistAndFlush(sampleFile1);
      entityManager.persistAndFlush(sampleFile2);
      entityManager.persistAndFlush(sampleFile3);

      List<ProcessingStatus> terminalStatuses =
          Arrays.asList(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED);

      List<ProcessedFile> oldFiles =
          processedFileRepository.findOldProcessedFiles(cutoffDate, terminalStatuses);

      assertThat(oldFiles).hasSize(2);
      assertThat(oldFiles)
          .extracting(ProcessedFile::getProvider)
          .containsExactlyInAnyOrder("agoda", "booking");
    }

    @Test
    @DisplayName("Should find stuck processing files")
    void shouldFindStuckProcessingFiles() {
      LocalDateTime cutoffDateTime = LocalDateTime.now().minusHours(2);

      // Create stuck file (processing for more than 2 hours)
      sampleFile1.setProcessingStatus(ProcessingStatus.IN_PROGRESS);
      sampleFile1.setProcessingStartedAt(LocalDateTime.now().minusHours(3));

      // Create recent processing file (should not be included)
      sampleFile2.setProcessingStatus(ProcessingStatus.IN_PROGRESS);
      sampleFile2.setProcessingStartedAt(LocalDateTime.now().minusMinutes(30));

      // Create completed file (should not be included)
      sampleFile3.setProcessingStatus(ProcessingStatus.COMPLETED);
      sampleFile3.setProcessingStartedAt(LocalDateTime.now().minusHours(4));

      entityManager.persistAndFlush(sampleFile1);
      entityManager.persistAndFlush(sampleFile2);
      entityManager.persistAndFlush(sampleFile3);

      List<ProcessedFile> stuckFiles =
          processedFileRepository.findStuckProcessingFiles(cutoffDateTime);

      assertThat(stuckFiles).hasSize(1);
      assertThat(stuckFiles.get(0).getProvider()).isEqualTo("agoda");
    }

    @Test
    @DisplayName("Should find recently processed files")
    void shouldFindRecentlyProcessedFiles() {
      LocalDateTime since = LocalDateTime.now().minusHours(2);

      // Create recent completed file
      sampleFile1.setCreatedAt(LocalDateTime.now().minusMinutes(30));
      sampleFile1.setProcessingStatus(ProcessingStatus.COMPLETED);

      // Create old completed file (should not be included)
      sampleFile2.setCreatedAt(LocalDateTime.now().minusHours(3));
      sampleFile2.setProcessingStatus(ProcessingStatus.COMPLETED);
      sampleFile2.setProvider("agoda");

      // Create recent failed file (should not be included)
      sampleFile3.setCreatedAt(LocalDateTime.now().minusMinutes(45));
      sampleFile3.setProcessingStatus(ProcessingStatus.FAILED);
      sampleFile3.setProvider("agoda");

      entityManager.persistAndFlush(sampleFile1);
      entityManager.persistAndFlush(sampleFile2);
      entityManager.persistAndFlush(sampleFile3);

      List<ProcessedFile> recentFiles =
          processedFileRepository.findRecentlyProcessedFiles(
              "agoda", since, ProcessingStatus.COMPLETED);

      assertThat(recentFiles).hasSize(1);
      assertThat(recentFiles.get(0).getId()).isEqualTo(sampleFile1.getId());
    }
  }

  @Nested
  @DisplayName("Delete Operations Tests")
  class DeleteOperationsTests {

    @Test
    @DisplayName("Should delete old records by date and status")
    void shouldDeleteOldRecordsByDateAndStatus() {
      LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

      // Create old files with terminal statuses
      sampleFile1.setCreatedAt(LocalDateTime.now().minusDays(35));
      sampleFile1.setProcessingStatus(ProcessingStatus.COMPLETED);

      sampleFile2.setCreatedAt(LocalDateTime.now().minusDays(32));
      sampleFile2.setProcessingStatus(ProcessingStatus.FAILED);

      // Create recent file - should not be deleted
      sampleFile3.setCreatedAt(LocalDateTime.now().minusDays(15));
      sampleFile3.setProcessingStatus(ProcessingStatus.COMPLETED);

      entityManager.persistAndFlush(sampleFile1);
      entityManager.persistAndFlush(sampleFile2);
      entityManager.persistAndFlush(sampleFile3);
      entityManager.clear();

      List<ProcessingStatus> terminalStatuses =
          Arrays.asList(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED);

      int deletedCount =
          processedFileRepository.deleteByCreatedAtBeforeAndProcessingStatusIn(
              cutoffDate, terminalStatuses);

      assertThat(deletedCount).isEqualTo(2);

      List<ProcessedFile> remainingFiles = processedFileRepository.findAll();
      assertThat(remainingFiles).hasSize(1);
      assertThat(remainingFiles.get(0).getProvider()).isEqualTo("expedia");
    }

    @Test
    @DisplayName("Should not delete files with non-terminal statuses")
    void shouldNotDeleteFilesWithNonTerminalStatuses() {
      LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

      // Create old file with non-terminal status
      sampleFile1.setCreatedAt(LocalDateTime.now().minusDays(35));
      sampleFile1.setProcessingStatus(ProcessingStatus.IN_PROGRESS);

      entityManager.persistAndFlush(sampleFile1);
      entityManager.clear();

      List<ProcessingStatus> terminalStatuses =
          Arrays.asList(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED);

      int deletedCount =
          processedFileRepository.deleteByCreatedAtBeforeAndProcessingStatusIn(
              cutoffDate, terminalStatuses);

      assertThat(deletedCount).isEqualTo(0);

      List<ProcessedFile> remainingFiles = processedFileRepository.findAll();
      assertThat(remainingFiles).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Constraint and Validation Tests")
  class ConstraintAndValidationTests {

    @Test
    @DisplayName("Should enforce unique constraint on s3Key and etag combination")
    void shouldEnforceUniqueConstraintOnS3KeyAndEtagCombination() {
      ProcessedFile file1 = sampleFile1.builder().build();
      ProcessedFile file2 =
          ProcessedFile.builder()
              .s3Key(sampleFile1.getS3Key()) // Same S3 key
              .etag(sampleFile1.getEtag()) // Same ETag
              .fileSize(2048L)
              .processingStatus(ProcessingStatus.PENDING)
              .provider("agoda")
              .build();

      processedFileRepository.save(file1);

      assertThatThrownBy(
              () -> {
                processedFileRepository.save(file2);
                entityManager.flush();
              })
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should allow same s3Key with different etag")
    void shouldAllowSameS3KeyWithDifferentEtag() {
      ProcessedFile file1 = sampleFile1.builder().build();
      ProcessedFile file2 =
          ProcessedFile.builder()
              .s3Key(sampleFile1.getS3Key()) // Same S3 key
              .etag("different-etag") // Different ETag
              .fileSize(2048L)
              .processingStatus(ProcessingStatus.PENDING)
              .provider("agoda")
              .build();

      processedFileRepository.save(file1);
      ProcessedFile saved = processedFileRepository.save(file2);

      assertThat(saved.getId()).isNotNull();

      List<ProcessedFile> allFiles = processedFileRepository.findAll();
      assertThat(allFiles).hasSize(2);
    }

    @Test
    @DisplayName("Should allow same etag with different s3Key")
    void shouldAllowSameEtagWithDifferentS3Key() {
      ProcessedFile file1 = sampleFile1.builder().build();
      ProcessedFile file2 =
          ProcessedFile.builder()
              .s3Key("different/s3/key.jl") // Different S3 key
              .etag(sampleFile1.getEtag()) // Same ETag
              .fileSize(2048L)
              .processingStatus(ProcessingStatus.PENDING)
              .provider("agoda")
              .build();

      processedFileRepository.save(file1);
      ProcessedFile saved = processedFileRepository.save(file2);

      assertThat(saved.getId()).isNotNull();

      List<ProcessedFile> allFiles = processedFileRepository.findAll();
      assertThat(allFiles).hasSize(2);
    }
  }

  @Nested
  @DisplayName("Auditing Tests")
  class AuditingTests {

    @Test
    @DisplayName("Should automatically set createdAt when saving new entity")
    void shouldAutomaticallySetCreatedAtWhenSavingNewEntity() {
      LocalDateTime beforeSave = LocalDateTime.now().minusSeconds(1);

      ProcessedFile saved = processedFileRepository.save(sampleFile1);

      LocalDateTime afterSave = LocalDateTime.now().plusSeconds(1);

      assertThat(saved.getCreatedAt()).isNotNull();
      assertThat(saved.getCreatedAt()).isBetween(beforeSave, afterSave);
    }

    @Test
    @DisplayName("Should automatically update updatedAt when modifying entity")
    void shouldAutomaticallyUpdateUpdatedAtWhenModifyingEntity() {
      ProcessedFile saved = processedFileRepository.save(sampleFile1);
      LocalDateTime originalUpdatedAt = saved.getUpdatedAt();

      // Small delay to ensure time difference
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      saved.setRecordsProcessed(150);
      ProcessedFile updated = processedFileRepository.save(saved);

      assertThat(updated.getUpdatedAt()).isNotNull();
      if (originalUpdatedAt != null) {
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
      }
    }

    @Test
    @DisplayName("Should not change createdAt when updating existing entity")
    void shouldNotChangeCreatedAtWhenUpdatingExistingEntity() {
      ProcessedFile saved = processedFileRepository.save(sampleFile1);
      LocalDateTime originalCreatedAt = saved.getCreatedAt();

      saved.setRecordsProcessed(150);
      ProcessedFile updated = processedFileRepository.save(saved);

      assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
    }
  }
}
