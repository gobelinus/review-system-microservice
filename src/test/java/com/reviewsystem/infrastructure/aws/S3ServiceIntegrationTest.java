package com.reviewsystem.infrastructure.aws;

import static org.assertj.core.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import com.reviewsystem.domain.service.FileTrackingService;
import com.reviewsystem.testcontainers.LocalStackTestContainer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// @SpringBootTest(classes = ReviewSystemMicroserviceApplication.class)
@Testcontainers
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class S3ServiceIntegrationTest {

  private static final String BUCKET_NAME = "test-reviews-bucket";
  private static final String PREFIX = "reviews/";
  private FileTrackingService fileTrackingService;

  @Container static LocalStackContainer localstack = LocalStackTestContainer.create();

  private S3Client s3Client;
  private S3Config s3Config;
  private S3Service s3Service;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("aws.s3.endpoint", () -> localstack.getEndpointOverride(S3).toString());
    registry.add("aws.s3.region", () -> localstack.getRegion());
    registry.add("aws.s3.access-key", () -> localstack.getAccessKey());
    registry.add("aws.s3.secret-key", () -> localstack.getSecretKey());
    registry.add("aws.s3.bucket-name", () -> BUCKET_NAME);
    registry.add("aws.s3.prefix", () -> PREFIX);
    registry.add("aws.s3.path-style-access", () -> "true");
  }

  public void clearBucket(String bucketName) throws Exception {
    // Runs: awslocal s3 rm s3://bucketName --recursive
    localstack.execInContainer("awslocal", "s3", "rm", "s3://" + bucketName, "--recursive");
  }

  @BeforeEach
  void setUp() {
    // try to clear existing bucket if any
    try {
      clearBucket(BUCKET_NAME);
    } catch (Exception e) {
      // do nothing
    }

    // Create S3 client for LocalStack
    s3Client =
        S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(S3))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .forcePathStyle(true)
            .build();

    // Create S3Config
    s3Config =
        S3Config.builder()
            .bucketName(BUCKET_NAME)
            .prefix(PREFIX)
            .region(localstack.getRegion())
            .accessKey(localstack.getAccessKey())
            .secretKey(localstack.getSecretKey())
            .endpoint("http://localhost:4566")
            .pathStyleAccess(true)
            .retryAttempts(3)
            .connectionTimeoutMs(30L)
            .socketTimeoutMs(60L)
            .endpoint(localstack.getEndpointOverride(S3).toString())
            .build();
    s3Service = new S3Service(s3Client, fileTrackingService, s3Config);

    // Create bucket
    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
  }

  @Nested
  @DisplayName("Download File Tests")
  class DownloadFileTests {
    @Test
    void downloadFile_shouldReturnFileContent_whenFileExists() throws IOException {
      // Given
      String expectedContent =
          """
                      {"review_id":"123","rating":5,"comment":"Great hotel"}
                      {"review_id":"124","rating":4,"comment":"Good location"}
                      """;
      String key = "reviews/test-download.jl";
      uploadFile(key, expectedContent);

      // When
      InputStream inputStream = s3Service.downloadFile(key);

      // Then
      String actualContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(actualContent).isEqualTo(expectedContent);
    }

    @Test
    void downloadFile_shouldThrowException_whenFileDoesNotExist() {
      // Given
      String nonExistentKey = "reviews/non-existent-file.jl";

      // When & Then
      assertThatThrownBy(() -> s3Service.downloadFile(nonExistentKey))
          .isInstanceOf(com.reviewsystem.presentation.exception.FileProcessingException.class)
          .hasMessageContaining("File not found");
    }
  }

  @Nested
  @DisplayName("List File Tests")
  class ListFileTests {
    @Test
    void listFiles_shouldReturnAllJsonlFiles_whenFilesExistInS3() {
      // Given
      String file1Content =
          """
                      {"review_id":"123","rating":5,"comment":"Great hotel"}
                      {"review_id":"124","rating":4,"comment":"Good location"}
                      """;

      String file2Content =
          """
                      {"review_id":"125","rating":3,"comment":"Average service"}
                      """;

      uploadFile("reviews/2024/01/reviews-2024-01-01.jl", file1Content);
      uploadFile("reviews/2024/01/reviews-2024-01-02.jl", file2Content);
      uploadFile("reviews/2024/01/not-a-review.txt", "This should be ignored");

      // When
      List<S3FileMetadata> files = s3Service.listFiles();

      // Then
      assertThat(files).hasSize(2);
      assertThat(files)
          .extracting(S3FileMetadata::getKey)
          .containsExactlyInAnyOrder(
              "reviews/2024/01/reviews-2024-01-01.jl", "reviews/2024/01/reviews-2024-01-02.jl");

      assertThat(files)
          .allMatch(file -> file.getSize() > 0)
          .allMatch(file -> file.getLastModified() != null)
          .allMatch(file -> file.getETag() != null)
          .allMatch(file -> file.getBucketName().equals(BUCKET_NAME));
    }

    @Test
    void listFiles_shouldHandleLargeNumberOfFiles_withPagination() {
      // Given - Upload 50 files to test pagination
      IntStream.range(1, 51)
          .forEach(
              i ->
                  uploadFile(
                      String.format("reviews/bulk/file-%03d.jl", i),
                      String.format("{\"review_id\":\"%d\",\"rating\":5}", i)));

      // When
      List<S3FileMetadata> files = s3Service.listFiles();

      // Then
      assertThat(files).hasSize(50);
      assertThat(files)
          .extracting(S3FileMetadata::getKey)
          .allMatch(key -> key.startsWith("reviews/bulk/file-"))
          .allMatch(key -> key.endsWith(".jl"));
    }

    @Test
    void listFiles_withAdditionalPrefix_shouldFilterCorrectly() {
      // Given
      uploadFile("reviews/2024/01/file1.jl", "content1");
      uploadFile("reviews/2024/02/file2.jl", "content2");
      uploadFile("reviews/2023/12/file3.jl", "content3");

      // When
      List<S3FileMetadata> files2024 = s3Service.listFiles("2024/");
      List<S3FileMetadata> files202401 = s3Service.listFiles("2024/01/");

      // Then
      assertThat(files2024).hasSize(2);
      assertThat(files202401).hasSize(1);
      assertThat(files202401.get(0).getKey()).contains("2024/01");
    }

    @Test
    void listFiles_shouldIgnoreEmptyFiles() {
      // Given
      uploadFile("reviews/empty-file.jl", ""); // Empty file
      uploadFile("reviews/valid-file.jl", "{\"review_id\":\"123\"}");

      // When
      List<S3FileMetadata> files = s3Service.listFiles();

      // Then
      assertThat(files).hasSize(1);
      assertThat(files.get(0).getKey()).isEqualTo("reviews/valid-file.jl");
    }

    @Test
    void listFiles_shouldIgnoreDirectories() {
      // Given
      uploadFile("reviews/subdir/file.jl", "content");
      // LocalStack automatically creates directory markers, but our service should ignore them

      // When
      List<S3FileMetadata> files = s3Service.listFiles();

      // Then
      assertThat(files).hasSize(1);
      assertThat(files.get(0).getKey()).isEqualTo("reviews/subdir/file.jl");
    }
  }

  @Nested
  @DisplayName("File Metadata and Existance Tests")
  class FileExistenceAndMetadataTest {
    @Test
    void getFileMetadata_shouldReturnCorrectMetadata_whenFileExists() {
      // Given
      String content = "Test content for metadata";
      String key = "reviews/metadata-test.jl";
      uploadFile(key, content);

      // When
      S3FileMetadata metadata = s3Service.getFileMetadata(key);

      // Then
      assertThat(metadata).isNotNull();
      assertThat(metadata.getKey()).isEqualTo(key);
      assertThat(metadata.getSize()).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
      assertThat(metadata.getBucketName()).isEqualTo(BUCKET_NAME);
      assertThat(metadata.getLastModified()).isNotNull();
      assertThat(metadata.getETag()).isNotNull();
    }

    @Test
    void fileExists_shouldReturnTrue_whenFileExists() {
      // Given
      String key = "reviews/exists-test.jl";
      uploadFile(key, "Test content");

      // When & Then
      assertThat(s3Service.fileExists(key)).isTrue();
    }

    @Test
    void fileExists_shouldReturnFalse_whenFileDoesNotExist() {
      // Given
      String key = "reviews/does-not-exist.jl";

      // When & Then
      assertThat(s3Service.fileExists(key)).isFalse();
    }

    @Test
    void getTotalSize_shouldReturnSumOfAllFileSizes() {
      // Given
      String content1 = "Small content";
      String content2 = "This is a longer content for testing size calculation";

      uploadFile("reviews/size-test-1.jl", content1);
      uploadFile("reviews/size-test-2.jl", content2);

      // When
      long totalSize = s3Service.getTotalSize();

      // Then
      long expectedSize =
          content1.getBytes(StandardCharsets.UTF_8).length
              + content2.getBytes(StandardCharsets.UTF_8).length;
      assertThat(totalSize).isEqualTo(expectedSize);
    }
  }

  @Nested
  @DisplayName("Concurrent Operations Tests")
  class ConcurrentFileOperationsTest {
    @Test
    void concurrentFileOperations_shouldHandleMultipleSimultaneousRequests() {
      // Given
      int numberOfFiles = 10;
      IntStream.range(1, numberOfFiles + 1)
          .forEach(
              i ->
                  uploadFile(
                      String.format("reviews/concurrent/file-%d.jl", i),
                      String.format("{\"review_id\":\"%d\"}", i)));

      ExecutorService executor = Executors.newFixedThreadPool(5);

      // When - Download files concurrently
      List<CompletableFuture<String>> futures =
          IntStream.range(1, numberOfFiles + 1)
              .mapToObj(
                  i ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            try (InputStream stream =
                                s3Service.downloadFile(
                                    String.format("reviews/concurrent/file-%d.jl", i))) {
                              return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                            } catch (Exception e) {
                              throw new RuntimeException(e);
                            }
                          },
                          executor))
              .toList();

      // Then
      assertThat(futures).hasSize(numberOfFiles);
      futures.forEach(
          future -> {
            assertThatNoException()
                .isThrownBy(
                    () -> {
                      String content = future.join();
                      assertThat(content).contains("review_id");
                    });
          });

      executor.shutdown();
    }
  }

  private void uploadFile(String key, String content) {
    s3Client.putObject(
        PutObjectRequest.builder().bucket(BUCKET_NAME).key(key).build(),
        software.amazon.awssdk.core.sync.RequestBody.fromInputStream(
            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
            content.getBytes(StandardCharsets.UTF_8).length));
  }
}
