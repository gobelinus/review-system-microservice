package com.reviewsystem.infrastructure.aws;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.reviewsystem.presentation.exception.FileProcessingException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

  @Mock private S3Config s3Config;

  @Mock private ListObjectsV2Iterable listObjectsIterable;

  private S3Service s3Service;

  private static final String BUCKET_NAME = "test-bucket";
  private static final String PREFIX = "reviews/";

  @BeforeEach
  void setUp() {
    when(s3Config.getBucketName()).thenReturn(BUCKET_NAME);
    when(s3Config.getPrefix()).thenReturn(PREFIX);
    when(s3Config.getMaxRetries()).thenReturn(3);
    when(s3Config.getRetryDelayMs()).thenReturn(1000L);

    s3Service = new S3Service(s3Client, s3Config);
  }

  @Test
  void listFiles_shouldReturnFileMetadataList_whenFilesExist() {
    // Given
    S3Object file1 =
        S3Object.builder()
            .key("reviews/2024/01/reviews-2024-01-01.jl")
            .lastModified(Instant.now())
            .size(1024L)
            .eTag("etag1")
            .build();

    S3Object file2 =
        S3Object.builder()
            .key("reviews/2024/01/reviews-2024-01-02.jl")
            .lastModified(Instant.now())
            .size(2048L)
            .eTag("etag2")
            .build();

    ListObjectsV2Response response =
        ListObjectsV2Response.builder()
            .contents(Arrays.asList(file1, file2))
            .isTruncated(false)
            .build();

    when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
        .thenReturn(listObjectsIterable);
    when(listObjectsIterable.stream()).thenReturn(Arrays.asList(response).stream());

    // When
    List<S3FileMetadata> result = s3Service.listFiles();

    // Then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getKey()).isEqualTo("reviews/2024/01/reviews-2024-01-01.jl");
    assertThat(result.get(0).getSize()).isEqualTo(1024L);
    assertThat(result.get(1).getKey()).isEqualTo("reviews/2024/01/reviews-2024-01-02.jl");
    assertThat(result.get(1).getSize()).isEqualTo(2048L);

    verify(s3Client).listObjectsV2Paginator(any(ListObjectsV2Request.class));
  }

  @Test
  void listFiles_shouldReturnEmptyList_whenNoFilesExist() {
    // Given
    ListObjectsV2Response response =
        ListObjectsV2Response.builder().contents(List.of()).isTruncated(false).build();

    when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
        .thenReturn(listObjectsIterable);
    when(listObjectsIterable.stream()).thenReturn(Arrays.asList(response).stream());

    // When
    List<S3FileMetadata> result = s3Service.listFiles();

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void listFiles_shouldHandlePagination_whenManyFilesExist() {
    // Given
    S3Object file1 =
        S3Object.builder().key("reviews/file1.jl").lastModified(Instant.now()).size(1024L).build();

    S3Object file2 =
        S3Object.builder().key("reviews/file2.jl").lastModified(Instant.now()).size(2048L).build();

    ListObjectsV2Response page1 =
        ListObjectsV2Response.builder()
            .contents(List.of(file1))
            .isTruncated(true)
            .nextContinuationToken("token1")
            .build();

    ListObjectsV2Response page2 =
        ListObjectsV2Response.builder().contents(List.of(file2)).isTruncated(false).build();

    when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
        .thenReturn(listObjectsIterable);
    when(listObjectsIterable.stream()).thenReturn(Arrays.asList(page1, page2).stream());

    // When
    List<S3FileMetadata> result = s3Service.listFiles();

    // Then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getKey()).isEqualTo("reviews/file1.jl");
    assertThat(result.get(1).getKey()).isEqualTo("reviews/file2.jl");
  }

  @Test
  void listFiles_shouldThrowException_whenS3ClientFails() {
    // Given
    when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
        .thenThrow(SdkException.builder().message("Network error").build());

    // When & Then
    assertThatThrownBy(() -> s3Service.listFiles())
        .isInstanceOf(FileProcessingException.class)
        .hasMessageContaining("Failed to list files from S3")
        .hasCauseInstanceOf(SdkException.class);
  }

  @Test
  void downloadFile_shouldReturnInputStream_whenFileExists() throws Exception {
    // Given
    String key = "reviews/test-file.jl";
    String content = "test content";
    ResponseInputStream<GetObjectResponse> responseInputStream =
        new ResponseInputStream<>(
            GetObjectResponse.builder().build(), new ByteArrayInputStream(content.getBytes()));

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

    // When
    InputStream result = s3Service.downloadFile(key);

    // Then
    assertThat(result).isNotNull();

    // Verify the correct request was made
    ArgumentCaptor<GetObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3Client).getObject(requestCaptor.capture());

    GetObjectRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.bucket()).isEqualTo(BUCKET_NAME);
    assertThat(capturedRequest.key()).isEqualTo(key);
  }

  @Test
  void downloadFile_shouldThrowException_whenFileNotFound() {
    // Given
    String key = "reviews/non-existent-file.jl";

    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("File not found").build());

    // When & Then
    assertThatThrownBy(() -> s3Service.downloadFile(key))
        .isInstanceOf(FileProcessingException.class)
        .hasMessageContaining("File not found: " + key)
        .hasCauseInstanceOf(NoSuchKeyException.class);
  }

  @Test
  void downloadFile_shouldRetryOnTransientFailures() {
    // Given
    String key = "reviews/test-file.jl";
    String content = "test content";

    // First two calls fail, third succeeds
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(SdkException.builder().message("Network timeout").build())
        .thenThrow(SdkException.builder().message("Service unavailable").build())
        .thenReturn(
            new ResponseInputStream<>(
                GetObjectResponse.builder().build(), new ByteArrayInputStream(content.getBytes())));

    // When
    InputStream result = s3Service.downloadFile(key);

    // Then
    assertThat(result).isNotNull();
    verify(s3Client, times(3)).getObject(any(GetObjectRequest.class));
  }

  @Test
  void downloadFile_shouldThrowException_afterMaxRetries() {
    // Given
    String key = "reviews/test-file.jl";

    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(SdkException.builder().message("Network error").build());

    // When & Then
    assertThatThrownBy(() -> s3Service.downloadFile(key))
        .isInstanceOf(FileProcessingException.class)
        .hasMessageContaining("Failed to download file after retries");

    verify(s3Client, times(4)).getObject(any(GetObjectRequest.class)); // initial + 3 retries
  }

  @Test
  void downloadFile_shouldThrowException_whenKeyIsNull() {
    // When & Then
    assertThatThrownBy(() -> s3Service.downloadFile(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("File key cannot be null or empty");
  }

  @Test
  void downloadFile_shouldThrowException_whenKeyIsEmpty() {
    // When & Then
    assertThatThrownBy(() -> s3Service.downloadFile(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("File key cannot be null or empty");
  }

  @Test
  void listFiles_shouldFilterByPrefix() {
    // Given
    S3Object file1 =
        S3Object.builder()
            .key("reviews/2024/file1.jl")
            .lastModified(Instant.now())
            .size(1024L)
            .build();

    ListObjectsV2Response response =
        ListObjectsV2Response.builder().contents(List.of(file1)).isTruncated(false).build();

    when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
        .thenReturn(listObjectsIterable);
    when(listObjectsIterable.stream()).thenReturn(Arrays.asList(response).stream());

    // When
    s3Service.listFiles();

    // Then
    ArgumentCaptor<ListObjectsV2Request> requestCaptor =
        ArgumentCaptor.forClass(ListObjectsV2Request.class);
    verify(s3Client).listObjectsV2Paginator(requestCaptor.capture());

    ListObjectsV2Request capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.bucket()).isEqualTo(BUCKET_NAME);
    assertThat(capturedRequest.prefix()).isEqualTo(PREFIX);
  }

  @Test
  void getFileMetadata_shouldReturnMetadata_whenFileExists() {
    // Given
    String key = "reviews/test-file.jl";
    HeadObjectResponse headResponse =
        HeadObjectResponse.builder()
            .contentLength(1024L)
            .lastModified(Instant.now())
            .eTag("etag123")
            .build();

    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headResponse);

    // When
    S3FileMetadata result = s3Service.getFileMetadata(key);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getKey()).isEqualTo(key);
    assertThat(result.getSize()).isEqualTo(1024L);
    assertThat(result.getETag()).isEqualTo("etag123");
  }

  @Test
  void getFileMetadata_shouldThrowException_whenFileNotFound() {
    // Given
    String key = "reviews/non-existent-file.jl";

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("File not found").build());

    // When & Then
    assertThatThrownBy(() -> s3Service.getFileMetadata(key))
        .isInstanceOf(FileProcessingException.class)
        .hasMessageContaining("File metadata not found: " + key);
  }
}
