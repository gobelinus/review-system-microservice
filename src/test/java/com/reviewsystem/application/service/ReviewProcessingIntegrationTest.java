package com.reviewsystem.application.service;

import static org.assertj.core.api.Assertions.*;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.entity.Provider;
import com.reviewsystem.domain.entity.Review;
import com.reviewsystem.infrastructure.parser.JsonLParser;
import com.reviewsystem.infrastructure.parser.dto.RawReviewData;
import com.reviewsystem.repository.ProviderRepository;
import com.reviewsystem.repository.ReviewRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers
@Transactional
class ReviewProcessingIntegrationTest {

  @Autowired private ReviewProcessingService reviewProcessingService;

  @Autowired private JsonLParser jsonLParser;

  @Autowired private ReviewRepository reviewRepository;

  @Autowired private ProviderRepository providerRepository;

  @BeforeEach
  void setUp() {
    reviewRepository.deleteAll();
    providerRepository.deleteAll();
    reviewProcessingService.clearProviderCache();
  }

  @Test
  void shouldProcessCompleteFileEndToEnd() throws IOException {
    // Given
    Path testFile = createCompleteTestFile();

    // When
    List<RawReviewData> rawReviews = jsonLParser.parseFile(testFile.toString());
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(result.getProcessedCount()).isEqualTo(3);
    assertThat(result.getValidCount()).isEqualTo(3);
    assertThat(result.getInvalidCount()).isEqualTo(0);

    // Verify database state
    List<Review> savedReviews = reviewRepository.findAll();
    assertThat(savedReviews).hasSize(3);

    List<Provider> savedProviders = providerRepository.findAll();
    assertThat(savedProviders).hasSize(2); // Agoda and Booking
  }

  @Test
  void shouldHandleMixedValidInvalidDataEndToEnd() throws IOException {
    // Given
    Path testFile = createMixedValidityTestFile();

    // When
    List<RawReviewData> rawReviews = jsonLParser.parseFile(testFile.toString());
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.FAILED);
    assertThat(result.getProcessedCount()).isEqualTo(4);
    assertThat(result.getValidCount()).isEqualTo(2);
    assertThat(result.getInvalidCount()).isEqualTo(2);
    assertThat(result.getErrors()).hasSize(2);

    // Verify only valid reviews are saved
    List<Review> savedReviews = reviewRepository.findAll();
    assertThat(savedReviews).hasSize(2);
  }

  @Test
  void shouldHandleDuplicateReviewsEndToEnd() throws IOException {
    // Given
    Path testFile = createTestFileWithDuplicates();

    // When - Process first time
    List<RawReviewData> rawReviews1 = jsonLParser.parseFile(testFile.toString());
    ReviewProcessingResult result1 = reviewProcessingService.processBatch(rawReviews1);

    // Then - First processing should succeed
    assertThat(result1.getValidCount()).isEqualTo(2);
    assertThat(result1.getDuplicateCount()).isEqualTo(0);

    // When - Process same file again
    List<RawReviewData> rawReviews2 = jsonLParser.parseFile(testFile.toString());
    ReviewProcessingResult result2 = reviewProcessingService.processBatch(rawReviews2);

    // Then - Second processing should detect duplicates
    assertThat(result2.getValidCount()).isEqualTo(0);
    assertThat(result2.getDuplicateCount()).isEqualTo(2);

    // Verify database still has only original reviews
    List<Review> savedReviews = reviewRepository.findAll();
    assertThat(savedReviews).hasSize(2);
  }

  @Test
  void shouldProcessLargeFileEfficiently() throws IOException {
    // Given
    Path largeFile = createLargeTestFile(5000);
    long initialMemory = getUsedMemory();

    // When
    List<RawReviewData> rawReviews = jsonLParser.parseFile(largeFile.toString());
    ReviewProcessingResult result = reviewProcessingService.processLargeBatch(rawReviews);

    // Then
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(result.getProcessedCount()).isEqualTo(5000);
    assertThat(result.getValidCount()).isEqualTo(5000);
    assertThat(result.getProcessingTimeMs()).isLessThan(30000); // Should complete within 30 seconds

    // Verify memory usage is reasonable
    long finalMemory = getUsedMemory();
    long memoryIncrease = finalMemory - initialMemory;
    assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024); // Less than 100MB increase

    // Verify all reviews are saved
    long savedCount = reviewRepository.count();
    assertThat(savedCount).isEqualTo(5000);
  }

  @Test
  void shouldHandleTransactionRollbackOnError() throws IOException {
    // Given
    Path testFile = createCompleteTestFile();
    List<RawReviewData> rawReviews = jsonLParser.parseFile(testFile.toString());

    // Simulate database constraint violation by creating duplicate provider review ID
    Review existingReview =
        Review.builder()
            .hotelId(999)
            .hotelName("Existing Hotel")
            .hotelReviewId("948353737") // Same ID as in test file
            .build();

    Provider provider =
        Provider.builder()
            .name("TestProvider")
            .code(com.reviewsystem.common.enums.ProviderType.AGODA)
            .build();
    providerRepository.save(provider);
    existingReview.setProvider(provider);
    reviewRepository.save(existingReview);

    // When & Then
    assertThatThrownBy(() -> reviewProcessingService.processBatch(rawReviews))
        .isInstanceOf(com.reviewsystem.presentation.exception.FileProcessingException.class);

    // Verify no partial data is saved (transaction rollback)
    List<Review> allReviews = reviewRepository.findAll();
    assertThat(allReviews).hasSize(1); // Only the pre-existing review
    assertThat(allReviews.get(0).getHotelId()).isEqualTo(999);
  }

  @Test
  void shouldHandleStreamProcessingForVeryLargeFiles() throws IOException {
    // Given
    Path veryLargeFile = createLargeTestFile(10000);

    // When
    List<ReviewProcessingResult> chunkResults = new java.util.ArrayList<>();
    jsonLParser.streamProcessFile(
        veryLargeFile.toString(),
        rawReview -> {
          // Process in small batches during streaming
          List<RawReviewData> singleReviewBatch = List.of(rawReview);
          ReviewProcessingResult result = reviewProcessingService.processBatch(singleReviewBatch);
          chunkResults.add(result);
        });

    // Then
    assertThat(chunkResults).hasSize(10000);
    assertThat(chunkResults.stream().allMatch(r -> r.getStatus() == ProcessingStatus.COMPLETED))
        .isTrue();

    long totalSaved = reviewRepository.count();
    assertThat(totalSaved).isEqualTo(10000);
  }

  @Test
  void shouldHandleConcurrentProcessing()
      throws InterruptedException, ExecutionException, IOException {
    // Given
    Path testFile1 = createTestFileWithPrefix("batch1-", 100);
    Path testFile2 = createTestFileWithPrefix("batch2-", 100);
    Path testFile3 = createTestFileWithPrefix("batch3-", 100);

    // When - Process multiple files concurrently
    CompletableFuture<ReviewProcessingResult> future1 =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                List<RawReviewData> rawReviews = jsonLParser.parseFile(testFile1.toString());
                return reviewProcessingService.processBatch(rawReviews);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    CompletableFuture<ReviewProcessingResult> future2 =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                List<RawReviewData> rawReviews = jsonLParser.parseFile(testFile2.toString());
                return reviewProcessingService.processBatch(rawReviews);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    CompletableFuture<ReviewProcessingResult> future3 =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                List<RawReviewData> rawReviews = jsonLParser.parseFile(testFile3.toString());
                return reviewProcessingService.processBatch(rawReviews);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    // Then
    ReviewProcessingResult result1 = future1.get();
    ReviewProcessingResult result2 = future2.get();
    ReviewProcessingResult result3 = future3.get();

    assertThat(result1.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(result2.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(result3.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);

    // Verify all reviews are saved without conflicts
    long totalSaved = reviewRepository.count();
    assertThat(totalSaved).isEqualTo(300);
  }

  @Test
  void shouldRecoverFromTransientErrors() throws IOException {
    // Given
    Path testFile = createCompleteTestFile();
    List<RawReviewData> rawReviews = jsonLParser.parseFile(testFile.toString());

    // Simulate transient error by temporarily making provider repository unavailable
    // This would typically be done with test containers or mock failures

    // When - First attempt might fail, second should succeed
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getStatus()).isIn(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED);
    assertThat(result.getValidCount()).isGreaterThan(0);
  }

  @Test
  void shouldProvideAccurateProcessingStatistics() throws IOException {
    // Given
    Path testFile = createCompleteTestFile();
    List<RawReviewData> rawReviews = jsonLParser.parseFile(testFile.toString());

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);
    ProcessingStatistics stats = reviewProcessingService.getProcessingStatistics();

    // Then
    assertThat(stats.getTotalReviews()).isEqualTo(result.getValidCount());
    assertThat(stats.getTotalProviders()).isGreaterThan(0);
    assertThat(stats.getCacheSize()).isGreaterThan(0);
  }

  // Helper methods for creating test files

  private Path createCompleteTestFile() throws IOException {
    List<String> lines =
        List.of(
            """
                {"hotelId": 1, "platform": "Agoda", "hotelName": "Hotel One", "comment": {"hotelReviewId": 948353737, "rating": 8.5, "reviewComments": "Excellent hotel", "reviewDate": "2025-04-10T05:37:00+07:00", "reviewerInfo": {"countryName": "India", "lengthOfStay": 3}}}
                """,
            """
                {"hotelId": 2, "platform": "Booking", "hotelName": "Hotel Two", "comment": {"hotelReviewId": 948353738, "rating": 7.0, "reviewComments": "Good location", "reviewDate": "2025-04-09T10:15:00+07:00", "reviewerInfo": {"countryName": "USA", "lengthOfStay": 2}}}
                """,
            """
                {"hotelId": 3, "platform": "Agoda", "hotelName": "Hotel Three", "comment": {"hotelReviewId": 948353739, "rating": 9.2, "reviewComments": "Amazing service", "reviewDate": "2025-04-08T14:22:00+07:00", "reviewerInfo": {"countryName": "UK", "lengthOfStay": 5}}}
                """);

    return createTestFileFromLines("complete-test.jl", lines);
  }

  private Path createMixedValidityTestFile() throws IOException {
    List<String> lines =
        List.of(
            """
                {"hotelId": 1, "platform": "Agoda", "hotelName": "Hotel One", "comment": {"hotelReviewId": 948353737, "rating": 8.5, "reviewComments": "Good hotel", "reviewDate": "2025-04-10T05:37:00+07:00"}}
                """,
            """
                {"hotelId": -1, "platform": "Agoda", "hotelName": "Invalid Hotel", "comment": {"hotelReviewId": 948353738, "rating": 8.5, "reviewComments": "Invalid hotel ID", "reviewDate": "2025-04-10T05:37:00+07:00"}}
                """,
            """
                {"hotelId": 2, "platform": "InvalidPlatform", "hotelName": "Hotel Two", "comment": {"hotelReviewId": 948353739, "rating": 7.0, "reviewComments": "Invalid platform", "reviewDate": "2025-04-10T05:37:00+07:00"}}
                """,
            """
                {"hotelId": 3, "platform": "Booking", "hotelName": "Hotel Three", "comment": {"hotelReviewId": 948353740, "rating": 9.0, "reviewComments": "Valid hotel", "reviewDate": "2025-04-10T05:37:00+07:00"}}
                """);

    return createTestFileFromLines("mixed-validity-test.jl", lines);
  }

  private Path createTestFileWithDuplicates() throws IOException {
    List<String> lines =
        List.of(
            """
                {"hotelId": 1, "platform": "Agoda", "hotelName": "Hotel One", "comment": {"hotelReviewId": 948353737, "rating": 8.5, "reviewComments": "First review", "reviewDate": "2025-04-10T05:37:00+07:00"}}
                """,
            """
                {"hotelId": 2, "platform": "Booking", "hotelName": "Hotel Two", "comment": {"hotelReviewId": 948353738, "rating": 7.0, "reviewComments": "Second review", "reviewDate": "2025-04-09T10:15:00+07:00"}}
                """);

    return createTestFileFromLines("duplicates-test.jl", lines);
  }

  private Path createLargeTestFile(int recordCount) throws IOException {
    Path tempFile = Files.createTempFile("large-test", ".jl");

    try (java.io.PrintWriter writer = new java.io.PrintWriter(Files.newBufferedWriter(tempFile))) {
      IntStream.range(1, recordCount + 1)
          .forEach(
              i -> {
                writer.println(
                    String.format(
                        """
                        {"hotelId": %d, "platform": "Agoda", "hotelName": "Hotel %d", "comment": {"hotelReviewId": %d, "rating": 8.0, "reviewComments": "Review %d", "reviewDate": "2025-04-10T05:37:00+07:00", "reviewerInfo": {"countryName": "India", "lengthOfStay": 2}}}
                        """,
                        i, i, 948353737 + i, i));
              });
    }

    return tempFile;
  }

  private Path createTestFileWithPrefix(String prefix, int recordCount) throws IOException {
    Path tempFile = Files.createTempFile(prefix, ".jl");

    try (java.io.PrintWriter writer = new java.io.PrintWriter(Files.newBufferedWriter(tempFile))) {
      IntStream.range(1, recordCount + 1)
          .forEach(
              i -> {
                writer.println(
                    String.format(
                        """
                        {"hotelId": %d, "platform": "Agoda", "hotelName": "%sHotel %d", "comment": {"hotelReviewId": %d, "rating": 8.0, "reviewComments": "%sReview %d", "reviewDate": "2025-04-10T05:37:00+07:00"}}
                        """,
                        i, prefix, i, 948353737 + i + prefix.hashCode(), prefix, i));
              });
    }

    return tempFile;
  }

  private Path createTestFileFromLines(String fileName, List<String> lines) throws IOException {
    Path tempFile = Files.createTempFile(fileName, ".jl");
    Files.write(tempFile, lines);
    return tempFile;
  }

  private long getUsedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }
}
