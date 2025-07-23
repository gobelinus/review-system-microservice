package com.reviewsystem.infrastructure.aws;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.reviewsystem.common.constants.ApplicationConstants;
import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.entity.ProcessedFile;
import com.reviewsystem.domain.service.FileTrackingService;
import com.reviewsystem.presentation.exception.FileProcessingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

  @Mock private S3Client s3Client;

  @Mock private FileTrackingService fileTrackingService;

  @Mock private S3Config s3Config;

  private S3Service s3Service;

  private static final String TEST_BUCKET = "test-review-bucket";
  private static final String TEST_PREFIX = "reviews/";
  private static final String TEST_FILE_KEY = "reviews/2024-01-01-reviews.jl";
  private static final String TEST_FILE_CONTENT =
      """
        {"reviewId": "1", "rating": 5, "comment": "Great hotel"}
        {"reviewId": "2", "rating": 4, "comment": "Good service"}
        """;

  @BeforeEach
  void setUp() {
    // Mock S3Config
    // when(s3Config.getBucketName()).thenReturn(TEST_BUCKET);
    // when(s3Config.getReviewsPrefix()).thenReturn(TEST_PREFIX);
    // when(s3Config.getRetryAttempts()).thenReturn(3);
    // when(s3Config.getRetryDelaySeconds()).thenReturn(2);

    s3Service = new S3Service(s3Client, fileTrackingService, s3Config);
  }

  @Nested
  @DisplayName("Download File Tests")
  class DownloadFileTests {
    @Test
    void downloadFile_ShouldReturnInputStream_WhenFileExistsInS3() throws IOException {
      // Arrange
      byte[] fileContent = TEST_FILE_CONTENT.getBytes();
      ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent);

      GetObjectResponse response =
          GetObjectResponse.builder()
              .contentLength((long) fileContent.length)
              .contentType("application/octet-stream")
              .build();

      ResponseInputStream<GetObjectResponse> responseInputStream =
          new ResponseInputStream<>(response, inputStream);

      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

      // Act
      InputStream result = s3Service.downloadFile(TEST_FILE_KEY);

      // Assert
      assertThat(result).isNotNull();

      byte[] resultContent = result.readAllBytes();
      assertThat(resultContent).isEqualTo(fileContent);

      // Verify S3 client was called with correct parameters
      ArgumentCaptor<GetObjectRequest> requestCaptor =
          ArgumentCaptor.forClass(GetObjectRequest.class);
      verify(s3Client).getObject(requestCaptor.capture());

      GetObjectRequest capturedRequest = requestCaptor.getValue();
      // assertThat(capturedRequest.bucket()).isEqualTo(TEST_BUCKET);
      assertThat(capturedRequest.key()).isEqualTo(TEST_FILE_KEY);
    }

    @Test
    void downloadFile_ShouldThrowFileProcessingException_WhenFileNotFound() {
      // Arrange
      NoSuchKeyException noSuchKeyException =
          NoSuchKeyException.builder().message("The specified key does not exist.").build();

      when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(noSuchKeyException);

      // Act & Assert
      assertThatThrownBy(() -> s3Service.downloadFile(TEST_FILE_KEY))
          .isInstanceOf(FileProcessingException.class)
          .hasMessageContaining("File not found")
          .hasMessageContaining(TEST_FILE_KEY)
          .hasCause(noSuchKeyException);
    }

    @Test
    void downloadFile_ShouldThrowFileProcessingException_WhenAccessDenied() {
      // Arrange
      S3Exception accessDeniedException =
          (S3Exception) S3Exception.builder().message("Access Denied").statusCode(403).build();

      when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(accessDeniedException);

      String errorMessage = String.format(ApplicationConstants.ERROR_ACCESS_DENIED, TEST_FILE_KEY);

      // Act & Assert
      assertThatThrownBy(() -> s3Service.downloadFile(TEST_FILE_KEY))
          .isInstanceOf(FileProcessingException.class)
          .hasMessageContaining(errorMessage)
          .hasMessageContaining(TEST_FILE_KEY)
          .hasCause(accessDeniedException);
    }

    @Test
    void downloadFile_ShouldThrowFileProcessingException_WhenNetworkError() {
      // Arrange
      SdkException networkException =
          SdkException.builder().message("Network error occurred").build();

      when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(networkException);

      // Act & Assert
      assertThatThrownBy(() -> s3Service.downloadFile(TEST_FILE_KEY))
          .isInstanceOf(FileProcessingException.class)
          .hasMessageContaining(ApplicationConstants.ERROR_NETWORK_FAILURE)
          .hasCause(networkException);
    }

    @Test
    void downloadFile_ShouldHandleEmptyFile_Gracefully() throws IOException {
      // Arrange
      byte[] emptyContent = new byte[0];
      ByteArrayInputStream inputStream = new ByteArrayInputStream(emptyContent);

      GetObjectResponse response =
          GetObjectResponse.builder()
              .contentLength(0L)
              .contentType("application/octet-stream")
              .build();

      ResponseInputStream<GetObjectResponse> responseInputStream =
          new ResponseInputStream<>(response, inputStream);

      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

      // Act
      InputStream result = s3Service.downloadFile(TEST_FILE_KEY);

      // Assert
      assertThat(result).isNotNull();
      byte[] resultContent = result.readAllBytes();
      assertThat(resultContent).isEmpty();
    }

    @Test
    void downloadFile_ShouldValidateParameters_WhenFileKeyIsNull() {
      // Act & Assert
      assertThatThrownBy(() -> s3Service.downloadFile(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("File key cannot be null or empty");
    }

    @Test
    void downloadFile_ShouldValidateParameters_WhenFileKeyIsEmpty() {
      // Act & Assert
      assertThatThrownBy(() -> s3Service.downloadFile(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("File key cannot be null or empty");
    }

    @Test
    void downloadFileWithRetry_ShouldRetryOnTransientFailures_AndSucceedEventually()
        throws IOException {
      // Arrange
      byte[] fileContent = TEST_FILE_CONTENT.getBytes();
      ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent);

      GetObjectResponse response =
          GetObjectResponse.builder()
              .contentLength((long) fileContent.length)
              .contentType("application/octet-stream")
              .build();

      ResponseInputStream<GetObjectResponse> responseInputStream =
          new ResponseInputStream<>(response, inputStream);

      // First two calls fail, third succeeds
      when(s3Client.getObject(any(GetObjectRequest.class)))
          .thenThrow(SdkException.builder().message("Temporary network error").build())
          .thenThrow(SdkException.builder().message("Temporary network error").build())
          .thenReturn(responseInputStream);

      // Act
      InputStream result = s3Service.downloadFileWithRetry(TEST_FILE_KEY);

      // Assert
      assertThat(result).isNotNull();
      verify(s3Client, times(3)).getObject(any(GetObjectRequest.class));
    }

    @Test
    void downloadFileWithRetry_ShouldFailAfterMaxRetries_WhenAllAttemptsFail() {
      // Arrange
      SdkException networkException =
          SdkException.builder().message("Persistent network error").build();

      when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(networkException);

      // Act & Assert
      assertThatThrownBy(() -> s3Service.downloadFileWithRetry(TEST_FILE_KEY))
          .isInstanceOf(FileProcessingException.class)
          .hasMessageContaining("Failed to download file after")
          .hasMessageContaining("attempts")
          .hasCause(networkException);

      verify(s3Client, times(3)).getObject(any(GetObjectRequest.class));
    }
  }

  @Nested
  @DisplayName("Listing Files Tests")
  class ListFileTests {
    @Test
    void listUnprocessedFiles_ShouldReturnOnlyUnprocessedFiles_WhenFilesExistInS3() {
      // Arrange
      S3Object processedFile =
          S3Object.builder()
              .key("reviews/2024-01-01-reviews.jl")
              .lastModified(Instant.now().minusSeconds(3600))
              .size(1024L)
              .build();

      S3Object unprocessedFile1 =
          S3Object.builder()
              .key("reviews/2024-01-02-reviews.jl")
              .lastModified(Instant.now().minusSeconds(1800))
              .size(2048L)
              .build();

      S3Object unprocessedFile2 =
          S3Object.builder()
              .key("reviews/2024-01-03-reviews.jl")
              .lastModified(Instant.now().minusSeconds(900))
              .size(1536L)
              .build();

      ListObjectsV2Response mockResponse =
          ListObjectsV2Response.builder()
              .contents(Arrays.asList(processedFile, unprocessedFile1, unprocessedFile2))
              .isTruncated(false)
              .build();

      ListObjectsV2Iterable mockIterable = mock(ListObjectsV2Iterable.class);
      when(mockIterable.stream()).thenReturn(Arrays.asList(mockResponse).stream());
      when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
          .thenReturn(mockIterable);

      // Mock file tracking service
      when(fileTrackingService.isFileProcessed("reviews/2024-01-01-reviews.jl")).thenReturn(true);
      when(fileTrackingService.isFileProcessed("reviews/2024-01-02-reviews.jl")).thenReturn(false);
      when(fileTrackingService.isFileProcessed("reviews/2024-01-03-reviews.jl")).thenReturn(false);

      // Act
      List<S3Object> result = s3Service.listUnprocessedFiles();

      // Assert
      assertThat(result).hasSize(2);
      assertThat(result)
          .extracting(S3Object::key)
          .containsExactlyInAnyOrder(
              "reviews/2024-01-02-reviews.jl", "reviews/2024-01-03-reviews.jl");

      // Verify S3 client was called with correct parameters
      ArgumentCaptor<ListObjectsV2Request> requestCaptor =
          ArgumentCaptor.forClass(ListObjectsV2Request.class);
      verify(s3Client).listObjectsV2Paginator(requestCaptor.capture());

      ListObjectsV2Request capturedRequest = requestCaptor.getValue();
      // assertThat(capturedRequest.bucket()).isEqualTo(TEST_BUCKET);
      assertThat(capturedRequest.prefix()).isEqualTo(TEST_PREFIX);
    }

    @Test
    void listUnprocessedFiles_ShouldReturnEmptyList_WhenNoFilesExistInS3() {
      // Arrange
      ListObjectsV2Response mockResponse =
          ListObjectsV2Response.builder()
              .contents(Collections.emptyList())
              .isTruncated(false)
              .build();

      ListObjectsV2Iterable mockIterable = mock(ListObjectsV2Iterable.class);
      when(mockIterable.stream()).thenReturn(Arrays.asList(mockResponse).stream());
      when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
          .thenReturn(mockIterable);

      // Act
      List<S3Object> result = s3Service.listUnprocessedFiles();

      // Assert
      assertThat(result).isEmpty();
      verify(fileTrackingService, never()).isFileProcessed(anyString());
    }

    @Test
    void listUnprocessedFiles_ShouldHandlePagination_WhenResponseIsTruncated() {
      // Arrange
      S3Object file1 =
          S3Object.builder()
              .key("reviews/2024-01-01-reviews.jl")
              .lastModified(Instant.now().minusSeconds(3600))
              .size(1024L)
              .build();

      S3Object file2 =
          S3Object.builder()
              .key("reviews/2024-01-02-reviews.jl")
              .lastModified(Instant.now().minusSeconds(1800))
              .size(2048L)
              .build();

      ListObjectsV2Response page1 =
          ListObjectsV2Response.builder()
              .contents(Arrays.asList(file1))
              .isTruncated(true)
              .nextContinuationToken("token123")
              .build();

      ListObjectsV2Response page2 =
          ListObjectsV2Response.builder().contents(Arrays.asList(file2)).isTruncated(false).build();

      ListObjectsV2Iterable mockIterable = mock(ListObjectsV2Iterable.class);
      when(mockIterable.stream()).thenReturn(Arrays.asList(page1, page2).stream());
      when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
          .thenReturn(mockIterable);

      when(fileTrackingService.isFileProcessed(anyString())).thenReturn(false);

      // Act
      List<S3Object> result = s3Service.listUnprocessedFiles();

      // Assert
      assertThat(result).hasSize(2);
      assertThat(result)
          .extracting(S3Object::key)
          .containsExactlyInAnyOrder(
              "reviews/2024-01-01-reviews.jl", "reviews/2024-01-02-reviews.jl");
    }

    @Test
    void listUnprocessedFiles_ShouldThrowFileProcessingException_WhenS3CallFails() {
      // Arrange
      S3Exception s3Exception =
          (S3Exception)
              S3Exception.builder().message("Internal Server Error").statusCode(500).build();

      when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenThrow(s3Exception);

      // Act & Assert
      assertThatThrownBy(() -> s3Service.listUnprocessedFiles())
          .isInstanceOf(FileProcessingException.class)
          .hasMessageContaining("Failed to list files from S3")
          .hasCause(s3Exception);
    }
  }

  @Nested
  @DisplayName("File Processing Tests")
  class ProcessFileTests {
    @Test
    void markFileAsProcessed_ShouldCallFileTrackingService_WithCorrectParameters() {
      // Arrange
      ProcessedFile processedFile =
          ProcessedFile.builder()
              .fileName(TEST_FILE_KEY)
              .filePath(TEST_FILE_KEY)
              .fileSize(1024L)
              .processedAt(LocalDateTime.now())
              .status(ProcessingStatus.COMPLETED)
              .recordsProcessed(100)
              .recordsSkipped(5)
              .build();

      // Act
      s3Service.markFileAsProcessed(TEST_FILE_KEY, 1024L, 100, 5);

      // Assert
      ArgumentCaptor<ProcessedFile> captor = ArgumentCaptor.forClass(ProcessedFile.class);
      verify(fileTrackingService).markFileAsProcessed(captor.capture());

      ProcessedFile captured = captor.getValue();
      assertThat(captured.getFileName()).isEqualTo(TEST_FILE_KEY);
      assertThat(captured.getFilePath()).isEqualTo(TEST_FILE_KEY);
      assertThat(captured.getFileSize()).isEqualTo(1024L);
      assertThat(captured.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
      assertThat(captured.getRecordsProcessed()).isEqualTo(100);
      assertThat(captured.getRecordsSkipped()).isEqualTo(5);
      assertThat(captured.getProcessedAt()).isNotNull();
    }

    @Test
    void markFileAsFailed_ShouldCallFileTrackingService_WithFailedStatus() {
      // Arrange
      String errorMessage = "Processing failed due to invalid JSON";

      // Act
      s3Service.markFileAsFailed(TEST_FILE_KEY, 1024L, errorMessage);

      // Assert
      ArgumentCaptor<ProcessedFile> captor = ArgumentCaptor.forClass(ProcessedFile.class);
      verify(fileTrackingService).markFileAsProcessed(captor.capture());

      ProcessedFile captured = captor.getValue();
      assertThat(captured.getFileName()).isEqualTo(TEST_FILE_KEY);
      assertThat(captured.getFilePath()).isEqualTo(TEST_FILE_KEY);
      assertThat(captured.getFileSize()).isEqualTo(1024L);
      assertThat(captured.getStatus()).isEqualTo(ProcessingStatus.FAILED);
      assertThat(captured.getErrorMessage()).isEqualTo(errorMessage);
      assertThat(captured.getProcessedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {
    @Test
    void isValidJsonLFile_ShouldReturnTrue_ForValidJsonLFiles() {
      // Arrange & Act & Assert
      assertThat(s3Service.isValidJsonLFile("reviews/2024-01-01-reviews.jl")).isTrue();
      assertThat(s3Service.isValidJsonLFile("data/reviews.jl")).isTrue();
      assertThat(s3Service.isValidJsonLFile("test.jl")).isTrue();
    }

    @Test
    void isValidJsonLFile_ShouldReturnFalse_ForInvalidFileTypes() {
      // Arrange & Act & Assert
      assertThat(s3Service.isValidJsonLFile("reviews/2024-01-01-reviews.json")).isFalse();
      assertThat(s3Service.isValidJsonLFile("reviews/2024-01-01-reviews.txt")).isFalse();
      assertThat(s3Service.isValidJsonLFile("reviews/2024-01-01-reviews.csv")).isFalse();
      assertThat(s3Service.isValidJsonLFile("reviews/2024-01-01-reviews")).isFalse();
      assertThat(s3Service.isValidJsonLFile("")).isFalse();
      assertThat(s3Service.isValidJsonLFile(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("Other Tests")
  class OtherTests {
    @Test
    void filterJsonLFiles_ShouldReturnOnlyJsonLFiles_WhenMixedFileTypesPresent() {
      // Arrange
      S3Object jsonlFile1 = S3Object.builder().key("reviews/2024-01-01-reviews.jl").build();
      S3Object jsonlFile2 = S3Object.builder().key("reviews/2024-01-02-reviews.jl").build();
      S3Object txtFile = S3Object.builder().key("reviews/readme.txt").build();
      S3Object csvFile = S3Object.builder().key("reviews/export.csv").build();

      List<S3Object> allFiles = Arrays.asList(jsonlFile1, jsonlFile2, txtFile, csvFile);

      ListObjectsV2Response mockResponse =
          ListObjectsV2Response.builder().contents(allFiles).isTruncated(false).build();

      ListObjectsV2Iterable mockIterable = mock(ListObjectsV2Iterable.class);
      when(mockIterable.stream()).thenReturn(Arrays.asList(mockResponse).stream());
      when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
          .thenReturn(mockIterable);

      when(fileTrackingService.isFileProcessed(anyString())).thenReturn(false);

      // Act
      List<S3Object> result = s3Service.listUnprocessedFiles();

      // Assert
      assertThat(result).hasSize(2);
      assertThat(result)
          .extracting(S3Object::key)
          .containsExactlyInAnyOrder(
              "reviews/2024-01-01-reviews.jl", "reviews/2024-01-02-reviews.jl");
    }

    @Test
    void getFileSize_ShouldReturnCorrectSize_WhenCalledWithValidFile() {
      // Arrange
      S3Object s3Object = S3Object.builder().key(TEST_FILE_KEY).size(1024L).build();

      // Act
      long result = s3Service.getFileSize(s3Object);

      // Assert
      assertThat(result).isEqualTo(1024L);
    }
  }
}
