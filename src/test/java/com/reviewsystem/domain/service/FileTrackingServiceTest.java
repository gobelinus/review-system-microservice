package com.reviewsystem.domain.service;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.entity.ProcessedFile;
import com.reviewsystem.domain.repository.ProcessedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileTrackingService Tests")
class FileTrackingServiceTest {

    @Mock
    private ProcessedFileRepository processedFileRepository;

    @InjectMocks
    private FileTrackingService fileTrackingService;

    private ProcessedFile sampleProcessedFile;
    private final String sampleS3Key = "reviews/agoda/2024/01/15/reviews_20240115.jl";
    private final String sampleEtag = "d41d8cd98f00b204e9800998ecf8427e";
    private final String sampleprovider = "agoda";

    @BeforeEach
    void setUp() {
        sampleProcessedFile = ProcessedFile.builder()
                .id(1L)
                .s3Key(sampleS3Key)
                .etag(sampleEtag)
                .fileSize(1024L)
                .lastModifiedDate(LocalDateTime.now().minusHours(1))
                .processingStatus(ProcessingStatus.COMPLETED)
                .provider(sampleprovider)
                .recordsProcessed(100)
                .recordsFailed(5)
                .build();

        // Set default configuration values
        ReflectionTestUtils.setField(fileTrackingService, "cleanupRetentionDays", 30);
        ReflectionTestUtils.setField(fileTrackingService, "stuckProcessingHours", 2);
    }

    @Nested
    @DisplayName("Duplicate File Detection Tests")
    class DuplicateFileDetectionTests {

        @Test
        @DisplayName("Should return true when file is already processed successfully")
        void shouldReturnTrueWhenFileIsAlreadyProcessedSuccessfully() {
            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.of(sampleProcessedFile));

            boolean result = fileTrackingService.isFileAlreadyProcessed(sampleS3Key, sampleEtag);

            assertThat(result).isTrue();
            verify(processedFileRepository).findByS3KeyAndEtag(sampleS3Key, sampleEtag);
        }

        @Test
        @DisplayName("Should return false when file is not found")
        void shouldReturnFalseWhenFileIsNotFound() {
            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.empty());

            boolean result = fileTrackingService.isFileAlreadyProcessed(sampleS3Key, sampleEtag);

            assertThat(result).isFalse();
            verify(processedFileRepository).findByS3KeyAndEtag(sampleS3Key, sampleEtag);
        }

        @Test
        @DisplayName("Should return false when file exists but processing failed")
        void shouldReturnFalseWhenFileExistsButProcessingFailed() {
            ProcessedFile failedFile = sampleProcessedFile.toBuilder()
                    .processingStatus(ProcessingStatus.FAILED)
                    .build();

            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.of(failedFile));

            boolean result = fileTrackingService.isFileAlreadyProcessed(sampleS3Key, sampleEtag);

            assertThat(result).isFalse();
            verify(processedFileRepository).findByS3KeyAndEtag(sampleS3Key, sampleEtag);
        }

        @Test
        @DisplayName("Should return false when file exists but is still processing")
        void shouldReturnFalseWhenFileExistsButIsStillProcessing() {
            ProcessedFile processingFile = sampleProcessedFile.toBuilder()
                    .processingStatus(ProcessingStatus.PROCESSING)
                    .build();

            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.of(processingFile));

            boolean result = fileTrackingService.isFileAlreadyProcessed(sampleS3Key, sampleEtag);

            assertThat(result).isFalse();
            verify(processedFileRepository).findByS3KeyAndEtag(sampleS3Key, sampleEtag);
        }

        @Test
        @DisplayName("Should return true when file is duplicate")
        void shouldReturnTrueWhenFileIsDuplicate() {
            when(processedFileRepository.existsByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(true);

            boolean result = fileTrackingService.isDuplicateFile(sampleS3Key, sampleEtag);

            assertThat(result).isTrue();
            verify(processedFileRepository).existsByS3KeyAndEtag(sampleS3Key, sampleEtag);
        }

        @Test
        @DisplayName("Should return false when file is not duplicate")
        void shouldReturnFalseWhenFileIsNotDuplicate() {
            when(processedFileRepository.existsByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(false);

            boolean result = fileTrackingService.isDuplicateFile(sampleS3Key, sampleEtag);

            assertThat(result).isFalse();
            verify(processedFileRepository).existsByS3KeyAndEtag(sampleS3Key, sampleEtag);
        }
    }

    @Nested
    @DisplayName("File Tracking Record Creation Tests")
    class FileTrackingRecordCreationTests {

        @Test
        @DisplayName("Should create new tracking record when file does not exist")
        void shouldCreateNewTrackingRecordWhenFileDoesNotExist() {
            LocalDateTime lastModified = LocalDateTime.now().minusHours(1);
            Long fileSize = 2048L;

            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.empty());
            when(processedFileRepository.save(any(ProcessedFile.class)))
                    .thenReturn(sampleProcessedFile);

            ProcessedFile result = fileTrackingService.createFileTrackingRecord(
                    sampleS3Key, sampleEtag, fileSize, lastModified, sampleprovider);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);

            ArgumentCaptor<ProcessedFile> captor = ArgumentCaptor.forClass(ProcessedFile.class);
            verify(processedFileRepository).save(captor.capture());

            ProcessedFile savedFile = captor.getValue();
            assertThat(savedFile.getS3Key()).isEqualTo(sampleS3Key);
            assertThat(savedFile.getEtag()).isEqualTo(sampleEtag);
            assertThat(savedFile.getFileSize()).isEqualTo(fileSize);
            assertThat(savedFile.getLastModifiedDate()).isEqualTo(lastModified);
            assertThat(savedFile.getprovider()).isEqualTo(sampleprovider);
            assertThat(savedFile.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
        }

        @Test
        @DisplayName("Should return existing record when file already exists")
        void shouldReturnExistingRecordWhenFileAlreadyExists() {
            LocalDateTime lastModified = LocalDateTime.now().minusHours(1);
            Long fileSize = 2048L;

            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.of(sampleProcessedFile));

            ProcessedFile result = fileTrackingService.createFileTrackingRecord(
                    sampleS3Key, sampleEtag, fileSize, lastModified, sampleprovider);

            assertThat(result).isEqualTo(sampleProcessedFile);
            verify(processedFileRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("File Processing Status Updates Tests")
    class FileProcessingStatusUpdatesTests {

        @Test
        @DisplayName("Should mark processing as started")
        void shouldMarkProcessingAsStarted() {
            Long fileId = 1L;
            ProcessedFile pendingFile = sampleProcessedFile.toBuilder()
                    .processingStatus(ProcessingStatus.PENDING)
                    .build();

            when(processedFileRepository.findById(fileId))
                    .thenReturn(Optional.of(pendingFile));
            when(processedFileRepository.save(any(ProcessedFile.class)))
                    .thenReturn(pendingFile);

            fileTrackingService.markProcessingStarted(fileId);

            ArgumentCaptor<ProcessedFile> captor = ArgumentCaptor.forClass(ProcessedFile.class);
            verify(processedFileRepository).save(captor.capture());

            ProcessedFile updatedFile = captor.getValue();
            assertThat(updatedFile.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
            assertThat(updatedFile.getProcessingStartedAt()).isNotNull();
            assertThat(updatedFile.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("Should throw exception when marking processing started for non-existent file")
        void shouldThrowExceptionWhenMarkingProcessingStartedForNonExistentFile() {
            Long fileId = 999L;

            when(processedFileRepository.findById(fileId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileTrackingService.markProcessingStarted(fileId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File not found with ID: 999");

            verify(processedFileRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should mark processing as completed")
        void shouldMarkProcessingAsCompleted() {
            Long fileId = 1L;
            int recordsProcessed = 150;
            int recordsFailed = 10;

            ProcessedFile processingFile = sampleProcessedFile.toBuilder()
                    .processingStatus(ProcessingStatus.PROCESSING)
                    .build();

            when(processedFileRepository.findById(fileId))
                    .thenReturn(Optional.of(processingFile));
            when(processedFileRepository.save(any(ProcessedFile.class)))
                    .thenReturn(processingFile);

            fileTrackingService.markProcessingCompleted(fileId, recordsProcessed, recordsFailed);

            ArgumentCaptor<ProcessedFile> captor = ArgumentCaptor.forClass(ProcessedFile.class);
            verify(processedFileRepository).save(captor.capture());

            ProcessedFile updatedFile = captor.getValue();
            assertThat(updatedFile.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
            assertThat(updatedFile.getProcessingCompletedAt()).isNotNull();
            assertThat(updatedFile.getRecordsProcessed()).isEqualTo(recordsProcessed);
            assertThat(updatedFile.getRecordsFailed()).isEqualTo(recordsFailed);
            assertThat(updatedFile.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("Should mark processing as failed")
        void shouldMarkProcessingAsFailed() {
            Long fileId = 1L;
            String errorMessage = "Database connection timeout";

            ProcessedFile processingFile = sampleProcessedFile.toBuilder()
                    .processingStatus(ProcessingStatus.PROCESSING)
                    .build();

            when(processedFileRepository.findById(fileId))
                    .thenReturn(Optional.of(processingFile));
            when(processedFileRepository.save(any(ProcessedFile.class)))
                    .thenReturn(processingFile);

            fileTrackingService.markProcessingFailed(fileId, errorMessage);

            ArgumentCaptor<ProcessedFile> captor = ArgumentCaptor.forClass(ProcessedFile.class);
            verify(processedFileRepository).save(captor.capture());

            ProcessedFile updatedFile = captor.getValue();
            assertThat(updatedFile.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
            assertThat(updatedFile.getProcessingCompletedAt()).isNotNull();
            assertThat(updatedFile.getErrorMessage()).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("Should throw exception when marking processing completed for non-existent file")
        void shouldThrowExceptionWhenMarkingProcessingCompletedForNonExistentFile() {
            Long fileId = 999L;

            when(processedFileRepository.findById(fileId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileTrackingService.markProcessingCompleted(fileId, 100, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File not found with ID: 999");

            verify(processedFileRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when marking processing failed for non-existent file")
        void shouldThrowExceptionWhenMarkingProcessingFailedForNonExistentFile() {
            Long fileId = 999L;

            when(processedFileRepository.findById(fileId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileTrackingService.markProcessingFailed(fileId, "Error"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File not found with ID: 999");

            verify(processedFileRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("File Processing Status Query Tests")
    class FileProcessingStatusQueryTests {

        @Test
        @DisplayName("Should return processing status when file exists")
        void shouldReturnProcessingStatusWhenFileExists() {
            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.of(sampleProcessedFile));

            Optional<ProcessingStatus> result = fileTrackingService
                    .getFileProcessingStatus(sampleS3Key, sampleEtag);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(ProcessingStatus.COMPLETED);
            verify(processedFileRepository).findByS3KeyAndEtag(sampleS3Key, sampleEtag);
        }

        @Test
        @DisplayName("Should return empty when file does not exist")
        void shouldReturnEmptyWhenFileDoesNotExist() {
            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.empty());

            Optional<ProcessingStatus> result = fileTrackingService
                    .getFileProcessingStatus(sampleS3Key, sampleEtag);

            assertThat(result).isEmpty();
            verify(processedFileRepository).findByS3KeyAndEtag(sampleS3Key, sampleEtag);
        }

        @Test
        @DisplayName("Should get files by status")
        void shouldGetFilesByStatus() {
            List<ProcessedFile> expectedFiles = Arrays.asList(sampleProcessedFile);

            when(processedFileRepository.findByProcessingStatus(ProcessingStatus.COMPLETED))
                    .thenReturn(expectedFiles);

            List<ProcessedFile> result = fileTrackingService
                    .getFilesByStatus(ProcessingStatus.COMPLETED);

            assertThat(result).isEqualTo(expectedFiles);
            verify(processedFileRepository).findByProcessingStatus(ProcessingStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should find file by S3 key")
        void shouldFindFileByS3Key() {
            when(processedFileRepository.findByS3Key(sampleS3Key))
                    .thenReturn(Optional.of(sampleProcessedFile));

            Optional<ProcessedFile> result = fileTrackingService.findByS3Key(sampleS3Key);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(sampleProcessedFile);
            verify(processedFileRepository).findByS3Key(sampleS3Key);
        }
    }

    @Nested
    @DisplayName("Processing Statistics Tests")
    class ProcessingStatisticsTests {

        @Test
        @DisplayName("Should get processing statistics for provider")
        void shouldGetProcessingStatisticsForprovider() {
            when(processedFileRepository.countByproviderAndProcessingStatus(sampleprovider, null))
                    .thenReturn(200L);
            when(processedFileRepository.countByproviderAndProcessingStatus(sampleprovider, ProcessingStatus.PENDING))
                    .thenReturn(10L);
            when(processedFileRepository.countByproviderAndProcessingStatus(sampleprovider, ProcessingStatus.PROCESSING))
                    .thenReturn(5L);
            when(processedFileRepository.countByproviderAndProcessingStatus(sampleprovider, ProcessingStatus.COMPLETED))
                    .thenReturn(170L);
            when(processedFileRepository.countByproviderAndProcessingStatus(sampleprovider, ProcessingStatus.FAILED))
                    .thenReturn(15L);

            FileTrackingService.ProcessingStatistics stats = fileTrackingService
                    .getProcessingStatistics(sampleprovider);

            assertThat(stats.getTotalFiles()).isEqualTo(200L);
            assertThat(stats.getPendingFiles()).isEqualTo(10L);
            assertThat(stats.getProcessingFiles()).isEqualTo(5L);
            assertThat(stats.getCompletedFiles()).isEqualTo(170L);
            assertThat(stats.getFailedFiles()).isEqualTo(15L);
            assertThat(stats.getSuccessRate()).isCloseTo(85.0, within(0.1));
            assertThat(stats.getFailureRate()).isCloseTo(7.5, within(0.1));
        }

        @Test
        @DisplayName("Should handle zero total files in statistics")
        void shouldHandleZeroTotalFilesInStatistics() {
            when(processedFileRepository.countByproviderAndProcessingStatus(eq(sampleprovider), any()))
                    .thenReturn(0L);

            FileTrackingService.ProcessingStatistics stats = fileTrackingService
                    .getProcessingStatistics(sampleprovider);

            assertThat(stats.getTotalFiles()).isEqualTo(0L);
            assertThat(stats.getSuccessRate()).isEqualTo(0.0);
            assertThat(stats.getFailureRate()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Cleanup of Old Records Tests")
    class CleanupOfOldRecordsTests {

        @Test
        @DisplayName("Should clean up old records successfully")
        void shouldCleanUpOldRecordsSuccessfully() {
            int expectedDeletedCount = 25;

            when(processedFileRepository.deleteByCreatedAtBeforeAndProcessingStatusIn(
                    any(LocalDateTime.class), anyList()))
                    .thenReturn(expectedDeletedCount);

            int actualDeletedCount = fileTrackingService.cleanupOldRecords();

            assertThat(actualDeletedCount).isEqualTo(expectedDeletedCount);

            ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<List<ProcessingStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);

            verify(processedFileRepository).deleteByCreatedAtBeforeAndProcessingStatusIn(
                    dateCaptor.capture(), statusCaptor.capture());

            LocalDateTime capturedDate = dateCaptor.getValue();
            List<ProcessingStatus> capturedStatuses = statusCaptor.getValue();

            // Should be approximately 30 days ago (allowing for test execution time)
            LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(30);
            assertThat(capturedDate).isBetween(
                    expectedCutoff.minusMinutes(1),
                    expectedCutoff.plusMinutes(1));

            assertThat(capturedStatuses).containsExactlyInAnyOrder(
                    ProcessingStatus.COMPLETED,
                    ProcessingStatus.FAILED,
                    ProcessingStatus.SKIPPED);
        }

        @Test
        @DisplayName("Should respect custom cleanup retention days")
        void shouldRespectCustomCleanupRetentionDays() {
            ReflectionTestUtils.setField(fileTrackingService, "cleanupRetentionDays", 60);

            when(processedFileRepository.deleteByCreatedAtBeforeAndProcessingStatusIn(
                    any(LocalDateTime.class), anyList()))
                    .thenReturn(10);

            fileTrackingService.cleanupOldRecords();

            ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(processedFileRepository).deleteByCreatedAtBeforeAndProcessingStatusIn(
                    dateCaptor.capture(), anyList());

            LocalDateTime capturedDate = dateCaptor.getValue();
            LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(60);

            assertThat(capturedDate).isBetween(
                    expectedCutoff.minusMinutes(1),
                    expectedCutoff.plusMinutes(1));
        }
    }

    @Nested
    @DisplayName("Stuck Files Detection and Reset Tests")
    class StuckFilesDetectionAndResetTests {

        @Test
        @DisplayName("Should find and reset stuck processing files")
        void shouldFindAndResetStuckProcessingFiles() {
            ProcessedFile stuckFile1 = ProcessedFile.builder()
                    .id(1L)
                    .s3Key("stuck/file1.jl")
                    .etag("etag1")
                    .fileSize(1024L)
                    .processingStatus(ProcessingStatus.PROCESSING)
                    .provider("agoda")
                    .build();

            ProcessedFile stuckFile2 = ProcessedFile.builder()
                    .id(2L)
                    .s3Key("stuck/file2.jl")
                    .etag("etag2")
                    .fileSize(2048L)
                    .processingStatus(ProcessingStatus.PROCESSING)
                    .provider("booking")
                    .build();

            List<ProcessedFile> stuckFiles = Arrays.asList(stuckFile1, stuckFile2);

            when(processedFileRepository.findStuckProcessingFiles(any(LocalDateTime.class)))
                    .thenReturn(stuckFiles);
            when(processedFileRepository.save(any(ProcessedFile.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            List<ProcessedFile> result = fileTrackingService.findAndResetStuckFiles();

            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(stuckFiles);

            ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(processedFileRepository).findStuckProcessingFiles(dateCaptor.capture());

            LocalDateTime capturedDate = dateCaptor.getValue();
            LocalDateTime expectedCutoff = LocalDateTime.now().minusHours(2);
            assertThat(capturedDate).isBetween(
                    expectedCutoff.minusMinutes(1),
                    expectedCutoff.plusMinutes(1));

            verify(processedFileRepository, times(2)).save(any(ProcessedFile.class));
        }

        @Test
        @DisplayName("Should respect custom stuck processing hours configuration")
        void shouldRespectCustomStuckProcessingHoursConfiguration() {
            ReflectionTestUtils.setField(fileTrackingService, "stuckProcessingHours", 4);

            when(processedFileRepository.findStuckProcessingFiles(any(LocalDateTime.class)))
                    .thenReturn(Arrays.asList());

            fileTrackingService.findAndResetStuckFiles();

            ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(processedFileRepository).findStuckProcessingFiles(dateCaptor.capture());

            LocalDateTime capturedDate = dateCaptor.getValue();
            LocalDateTime expectedCutoff = LocalDateTime.now().minusHours(4);

            assertThat(capturedDate).isBetween(
                    expectedCutoff.minusMinutes(1),
                    expectedCutoff.plusMinutes(1));
        }

        @Test
        @DisplayName("Should handle no stuck files gracefully")
        void shouldHandleNoStuckFilesGracefully() {
            when(processedFileRepository.findStuckProcessingFiles(any(LocalDateTime.class)))
                    .thenReturn(Arrays.asList());

            List<ProcessedFile> result = fileTrackingService.findAndResetStuckFiles();

            assertThat(result).isEmpty();
            verify(processedFileRepository, never()).save(any(ProcessedFile.class));
        }
    }

    @Nested
    @DisplayName("Recently Processed Files Tests")
    class RecentlyProcessedFilesTests {

        @Test
        @DisplayName("Should get recently processed files for provider")
        void shouldGetRecentlyProcessedFilesForprovider() {
            int hours = 24;
            List<ProcessedFile> expectedFiles = Arrays.asList(sampleProcessedFile);

            when(processedFileRepository.findRecentlyProcessedFiles(
                    eq(sampleprovider), any(LocalDateTime.class), eq(ProcessingStatus.COMPLETED)))
                    .thenReturn(expectedFiles);

            List<ProcessedFile> result = fileTrackingService
                    .getRecentlyProcessedFiles(sampleprovider, hours);

            assertThat(result).isEqualTo(expectedFiles);

            ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(processedFileRepository).findRecentlyProcessedFiles(
                    eq(sampleprovider), dateCaptor.capture(), eq(ProcessingStatus.COMPLETED));

            LocalDateTime capturedDate = dateCaptor.getValue();
            LocalDateTime expectedSince = LocalDateTime.now().minusHours(hours);
            assertThat(capturedDate).isBetween(
                    expectedSince.minusMinutes(1),
                    expectedSince.plusMinutes(1));
        }

        @Test
        @DisplayName("Should handle empty recently processed files")
        void shouldHandleEmptyRecentlyProcessedFiles() {
            when(processedFileRepository.findRecentlyProcessedFiles(
                    eq(sampleprovider), any(LocalDateTime.class), eq(ProcessingStatus.COMPLETED)))
                    .thenReturn(Arrays.asList());

            List<ProcessedFile> result = fileTrackingService
                    .getRecentlyProcessedFiles(sampleprovider, 12);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Processing Statistics Inner Class Tests")
    class ProcessingStatisticsInnerClassTests {

        @Test
        @DisplayName("Should calculate success rate correctly")
        void shouldCalculateSuccessRateCorrectly() {
            FileTrackingService.ProcessingStatistics stats = FileTrackingService.ProcessingStatistics.builder()
                    .totalFiles(100L)
                    .completedFiles(85L)
                    .failedFiles(15L)
                    .build();

            assertThat(stats.getSuccessRate()).isEqualTo(85.0);
        }

        @Test
        @DisplayName("Should calculate failure rate correctly")
        void shouldCalculateFailureRateCorrectly() {
            FileTrackingService.ProcessingStatistics stats = FileTrackingService.ProcessingStatistics.builder()
                    .totalFiles(100L)
                    .completedFiles(80L)
                    .failedFiles(20L)
                    .build();

            assertThat(stats.getFailureRate()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("Should return zero rates when total files is zero")
        void shouldReturnZeroRatesWhenTotalFilesIsZero() {
            FileTrackingService.ProcessingStatistics stats = FileTrackingService.ProcessingStatistics.builder()
                    .totalFiles(0L)
                    .completedFiles(0L)
                    .failedFiles(0L)
                    .build();

            assertThat(stats.getSuccessRate()).isEqualTo(0.0);
            assertThat(stats.getFailureRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should handle partial completion rates")
        void shouldHandlePartialCompletionRates() {
            FileTrackingService.ProcessingStatistics stats = FileTrackingService.ProcessingStatistics.builder()
                    .totalFiles(3L)
                    .completedFiles(2L)
                    .failedFiles(1L)
                    .build();

            assertThat(stats.getSuccessRate()).isCloseTo(66.67, within(0.01));
            assertThat(stats.getFailureRate()).isCloseTo(33.33, within(0.01));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle repository exceptions during file creation")
        void shouldHandleRepositoryExceptionsDuringFileCreation() {
            LocalDateTime lastModified = LocalDateTime.now().minusHours(1);
            Long fileSize = 2048L;

            when(processedFileRepository.findByS3KeyAndEtag(sampleS3Key, sampleEtag))
                    .thenReturn(Optional.empty());
            when(processedFileRepository.save(any(ProcessedFile.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            assertThatThrownBy(() -> fileTrackingService.createFileTrackingRecord(
                    sampleS3Key, sampleEtag, fileSize, lastModified, sampleprovider))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database connection failed");
        }

        @Test
        @DisplayName("Should handle repository exceptions during status updates")
        void shouldHandleRepositoryExceptionsDuringStatusUpdates() {
            Long fileId = 1L;

            when(processedFileRepository.findById(fileId))
                    .thenReturn(Optional.of(sampleProcessedFile));
            when(processedFileRepository.save(any(ProcessedFile.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            assertThatThrownBy(() -> fileTrackingService.markProcessingStarted(fileId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database connection failed");
        }

        @Test
        @DisplayName("Should handle repository exceptions during cleanup")
        void shouldHandleRepositoryExceptionsDuringCleanup() {
            when(processedFileRepository.deleteByCreatedAtBeforeAndProcessingStatusIn(
                    any(LocalDateTime.class), anyList()))
                    .thenThrow(new RuntimeException("Database connection failed"));

            assertThatThrownBy(() -> fileTrackingService.cleanupOldRecords())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database connection failed");
        }
    }
}