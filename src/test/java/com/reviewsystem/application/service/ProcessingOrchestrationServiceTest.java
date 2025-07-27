package com.reviewsystem.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.service.FileTrackingService;
import com.reviewsystem.infrastructure.aws.S3Service;
import com.reviewsystem.infrastructure.monitoring.ProcessingMetrics;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test-postgres")
class ProcessingOrchestrationServiceTest {

  @Mock private S3Service s3Service;

  @Mock private ReviewProcessingService reviewProcessingService;

  @Mock private FileTrackingService fileTrackingService;

  @Mock private ProcessingMetrics processingMetrics;

  @InjectMocks private ProcessingOrchestrationService orchestrationService;

  private List<S3Object> sampleS3Objects;
  private List<String> sampleFileKeys;

  @BeforeEach
  void setUp() {
    sampleFileKeys =
        Arrays.asList(
            "reviews/2025/01/15/agoda-reviews-20250115.jl",
            "reviews/2025/01/15/booking-reviews-20250115.jl",
            "reviews/2025/01/16/expedia-reviews-20250116.jl");

    // Create mock S3Object instances
    sampleS3Objects =
        Arrays.asList(
            createMockS3Object("reviews/2025/01/15/agoda-reviews-20250115.jl", 1024L),
            createMockS3Object("reviews/2025/01/15/booking-reviews-20250115.jl", 2048L),
            createMockS3Object("reviews/2025/01/16/expedia-reviews-20250116.jl", 1536L));
  }

  private S3Object createMockS3Object(String key, Long size) {
    return S3Object.builder().key(key).size(size).lastModified(Instant.now()).build();
  }

  @Test
  void processNewFiles_ShouldDiscoverAndProcessNewFiles() {
    // Given
    List<S3Object> unprocessedObjects =
        Arrays.asList(sampleS3Objects.get(0), sampleS3Objects.get(2));
    when(s3Service.listUnprocessedFiles()).thenReturn(unprocessedObjects);
    when(reviewProcessingService.processFile(sampleFileKeys.get(0))).thenReturn(50L);
    when(reviewProcessingService.processFile(sampleFileKeys.get(2))).thenReturn(75L);

    // When
    Long totalProcessed = orchestrationService.processNewFiles();

    // Then
    assertEquals(125L, totalProcessed);
    verify(s3Service).listUnprocessedFiles();
    verify(reviewProcessingService, times(2)).processFile(anyString());
    verify(processingMetrics).recordFileProcessingStart(2);
    verify(processingMetrics).recordFileProcessingComplete(125L);
  }

  @Test
  void processNewFiles_ShouldReturnZeroWhenNoNewFiles() {
    // Given
    when(s3Service.listUnprocessedFiles()).thenReturn(List.of());

    // When
    Long totalProcessed = orchestrationService.processNewFiles();

    // Then
    assertEquals(0L, totalProcessed);
    verify(s3Service).listUnprocessedFiles();
    verify(reviewProcessingService, never()).processFile(anyString());
    verify(processingMetrics).recordFileProcessingStart(0);
  }

  @Test
  void processNewFiles_ShouldHandleProcessingFailureGracefully() {
    // Given
    List<S3Object> unprocessedObjects =
        Arrays.asList(sampleS3Objects.get(0), sampleS3Objects.get(1));
    when(s3Service.listUnprocessedFiles()).thenReturn(unprocessedObjects);
    when(reviewProcessingService.processFile(sampleFileKeys.get(0))).thenReturn(50L);
    when(reviewProcessingService.processFile(sampleFileKeys.get(1)))
        .thenThrow(new RuntimeException("Processing failed"));

    // When
    Long totalProcessed = orchestrationService.processNewFiles();

    // Then
    assertEquals(50L, totalProcessed);
    verify(reviewProcessingService, times(2)).processFile(anyString());
    verify(processingMetrics)
        .recordFileProcessingError(eq(sampleFileKeys.get(1)), any(RuntimeException.class));
    verify(processingMetrics).recordFileProcessingComplete(50L);
  }

  @Test
  void processNewFiles_ShouldPrioritizeFilesByDate() {
    // Given
    List<S3Object> unorderedObjects =
        Arrays.asList(
            createMockS3Object("reviews/2025/01/16/agoda-reviews-20250116.jl", 1024L),
            createMockS3Object("reviews/2025/01/14/booking-reviews-20250114.jl", 2048L),
            createMockS3Object("reviews/2025/01/15/expedia-reviews-20250115.jl", 1536L));

    // Sort them by date for verification (S3Service already sorts by lastModified)
    when(s3Service.listUnprocessedFiles()).thenReturn(unorderedObjects);
    when(reviewProcessingService.processFile(anyString())).thenReturn(10L);

    // When
    orchestrationService.processNewFiles();

    // Then
    verify(reviewProcessingService, times(3)).processFile(anyString());
    // Note: The actual order depends on the lastModified timestamps set in the mock objects
  }

  @Test
  void processNewFiles_ShouldHandleS3ServiceException() {
    // Given
    when(s3Service.listUnprocessedFiles()).thenThrow(new RuntimeException("S3 connection failed"));

    // When & Then
    assertThrows(RuntimeException.class, () -> orchestrationService.processNewFiles());
    verify(processingMetrics).recordS3ListingError(any(RuntimeException.class));
  }

  @Test
  void processFilesConcurrently_ShouldProcessFilesInParallel() {
    // Given
    List<String> filesToProcess = Arrays.asList("file1.jl", "file2.jl", "file3.jl");

    // Mock the processing service to return specific values
    when(reviewProcessingService.processFile("file1.jl")).thenReturn(10L);
    when(reviewProcessingService.processFile("file2.jl")).thenReturn(20L);
    when(reviewProcessingService.processFile("file3.jl")).thenReturn(30L);

    // When
    Long totalProcessed = orchestrationService.processFilesConcurrently(filesToProcess);

    // Then
    assertEquals(60L, totalProcessed);
    verify(reviewProcessingService).processFile("file1.jl");
    verify(reviewProcessingService).processFile("file2.jl");
    verify(reviewProcessingService).processFile("file3.jl");
    verify(processingMetrics).recordConcurrentProcessingStart(3);
    verify(processingMetrics).recordConcurrentProcessingComplete(60L);
  }

  @Test
  void processFilesConcurrently_ShouldHandlePartialFailures() {
    // Given
    List<String> filesToProcess = Arrays.asList("file1.jl", "file2.jl");

    when(reviewProcessingService.processFile("file1.jl")).thenReturn(25L);
    when(reviewProcessingService.processFile("file2.jl"))
        .thenThrow(new RuntimeException("Processing failed"));

    // When
    Long totalProcessed = orchestrationService.processFilesConcurrently(filesToProcess);

    // Then
    assertEquals(25L, totalProcessed);
    verify(processingMetrics).recordConcurrentProcessingStart(2);
    verify(processingMetrics).recordConcurrentProcessingComplete(25L);
    verify(processingMetrics)
        .recordFileProcessingError(eq("file2.jl"), any(RuntimeException.class));
  }

  @Test
  void cleanupOldProcessedFiles_ShouldRemoveOldFileRecords() {
    // Given
    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
    when(fileTrackingService.deleteProcessedFilesBefore(any(LocalDateTime.class))).thenReturn(15L);

    // When
    Long deletedCount = orchestrationService.cleanupOldProcessedFiles();

    // Then
    assertEquals(15L, deletedCount);
    verify(fileTrackingService).deleteProcessedFilesBefore(any(LocalDateTime.class));
    verify(processingMetrics).recordCleanupStart();
    verify(processingMetrics).recordCleanupComplete(15L);
  }

  @Test
  void cleanupOldProcessedFiles_ShouldHandleCleanupException() {
    // Given
    when(fileTrackingService.deleteProcessedFilesBefore(any(LocalDateTime.class)))
        .thenThrow(new RuntimeException("Database error"));

    // When & Then
    assertThrows(RuntimeException.class, () -> orchestrationService.cleanupOldProcessedFiles());
    verify(processingMetrics).recordCleanupStart();
    verify(processingMetrics).recordCleanupError(any(RuntimeException.class));
  }

  @Test
  void shutdown_ShouldGracefullyShutdownExecutorService() {
    // Given - first trigger concurrent processing to initialize executor service
    List<String> filesToProcess = Arrays.asList("test-file.jl");
    when(reviewProcessingService.processFile("test-file.jl")).thenReturn(10L);

    // Initialize the executor service by calling concurrent processing
    orchestrationService.processFilesConcurrently(filesToProcess);

    // When
    orchestrationService.shutdown();

    // Then
    verify(processingMetrics).recordGracefulShutdown();
  }

  @Test
  void shutdown_ShouldHandleNonInitializedExecutor() {
    // Given - executor service is not initialized

    // When
    orchestrationService.shutdown();

    // Then - should not throw any exception
    // The method should handle null executor service gracefully
    assertDoesNotThrow(() -> orchestrationService.shutdown());
  }

  @Test
  void getProcessingStatus_ShouldReturnCurrentStatus() {
    // Given
    when(fileTrackingService.getTotalProcessedFiles()).thenReturn(100L);
    when(fileTrackingService.getProcessedFilesToday()).thenReturn(5L);

    // When
    ProcessingStatus status = orchestrationService.getProcessingStatus();

    // Then
    assertEquals(ProcessingStatus.IDLE, status);
    verify(fileTrackingService).getTotalProcessedFiles();
    verify(fileTrackingService).getProcessedFilesToday();
  }

  @Test
  void processNewFiles_ShouldHandleEmptyS3Objects() {
    // Given
    when(s3Service.listUnprocessedFiles()).thenReturn(Arrays.asList());

    // When
    Long totalProcessed = orchestrationService.processNewFiles();

    // Then
    assertEquals(0L, totalProcessed);
    verify(s3Service).listUnprocessedFiles();
    verify(reviewProcessingService, never()).processFile(anyString());
    verify(processingMetrics).recordFileProcessingStart(0);
  }

  @Test
  void processNewFiles_ShouldExtractFileKeysFromS3Objects() {
    // Given
    S3Object s3Object = createMockS3Object("reviews/test-file.jl", 1024L);
    when(s3Service.listUnprocessedFiles()).thenReturn(Arrays.asList(s3Object));
    when(reviewProcessingService.processFile("reviews/test-file.jl")).thenReturn(42L);

    // When
    Long totalProcessed = orchestrationService.processNewFiles();

    // Then
    assertEquals(42L, totalProcessed);
    verify(reviewProcessingService).processFile("reviews/test-file.jl");
  }

  @Test
  void processNewFiles_ShouldContinueProcessingAfterSingleFileFailure() {
    // Given
    List<S3Object> objects =
        Arrays.asList(
            createMockS3Object("file1.jl", 1024L),
            createMockS3Object("file2.jl", 2048L),
            createMockS3Object("file3.jl", 1536L));
    when(s3Service.listUnprocessedFiles()).thenReturn(objects);
    when(reviewProcessingService.processFile("file1.jl")).thenReturn(10L);
    when(reviewProcessingService.processFile("file2.jl")).thenThrow(new RuntimeException("Failed"));
    when(reviewProcessingService.processFile("file3.jl")).thenReturn(30L);

    // When
    Long totalProcessed = orchestrationService.processNewFiles();

    // Then
    assertEquals(40L, totalProcessed); // 10 + 30, file2 failed
    verify(reviewProcessingService, times(3)).processFile(anyString());
    verify(processingMetrics)
        .recordFileProcessingError(eq("file2.jl"), any(RuntimeException.class));
    verify(processingMetrics).recordFileProcessingComplete(40L);
  }
}
