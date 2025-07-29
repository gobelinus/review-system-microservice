package com.reviewsystem.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.Provider;
import com.reviewsystem.domain.entity.Review;
import com.reviewsystem.infrastructure.parser.ReviewDataTransformer;
import com.reviewsystem.infrastructure.parser.ReviewDataValidator;
import com.reviewsystem.infrastructure.parser.dto.RawReviewData;
import com.reviewsystem.infrastructure.parser.dto.ValidationResult;
import com.reviewsystem.presentation.exception.FileProcessingException;
import com.reviewsystem.repository.ProviderRepository;
import com.reviewsystem.repository.ReviewRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test-postgres")
@ExtendWith(MockitoExtension.class)
class ReviewProcessingServiceTest {

  @Mock private ReviewDataValidator validator;

  @Mock private ReviewDataTransformer transformer;

  @Mock private ReviewRepository reviewRepository;

  @Mock private ProviderRepository providerRepository;

  @InjectMocks private ReviewProcessingService reviewProcessingService;

  private Provider mockprovider;

  @BeforeEach
  void setUp() {
    mockprovider = Provider.builder().id(1L).name("Agoda").code(ProviderType.AGODA).build();
  }

  @Test
  void shouldProcessValidReviewBatch() {
    // Given
    List<RawReviewData> rawReviews = createValidRawReviewDataList();
    List<Review> transformedReviews = createTransformedReviews();

    when(validator.validate(any(RawReviewData.class))).thenReturn(ValidationResult.valid());
    when(providerRepository.findByName("Agoda")).thenReturn(Optional.of(mockprovider));
    when(transformer.transform(any(RawReviewData.class), eq(mockprovider)))
        .thenReturn(transformedReviews.get(0), transformedReviews.get(1));
    when(reviewRepository.saveAll(anyList())).thenReturn(transformedReviews);

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getProcessedCount()).isEqualTo(2);
    assertThat(result.getValidCount()).isEqualTo(2);
    assertThat(result.getInvalidCount()).isEqualTo(0);
    assertThat(result.getErrors()).isEmpty();
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);

    verify(validator, times(2)).validate(any(RawReviewData.class));
    verify(transformer, times(2)).transform(any(RawReviewData.class), eq(mockprovider));
  }

  @Test
  void shouldSkipInvalidReviewsInBatch() {
    // Given
    List<RawReviewData> rawReviews = createValidRawReviewDataList();
    List<Review> transformedReviews = createTransformedReviews();

    when(validator.validate(rawReviews.get(0))).thenReturn(ValidationResult.valid());
    when(validator.validate(rawReviews.get(1)))
        .thenReturn(ValidationResult.invalid(List.of("Invalid rating")));
    when(providerRepository.findByName("Agoda")).thenReturn(Optional.of(mockprovider));
    when(transformer.transform(rawReviews.get(0), mockprovider))
        .thenReturn(transformedReviews.get(0));
    when(reviewRepository.saveAll(anyList())).thenReturn(List.of(transformedReviews.get(0)));

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getProcessedCount()).isEqualTo(2);
    assertThat(result.getValidCount()).isEqualTo(1);
    assertThat(result.getInvalidCount()).isEqualTo(1);
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.FAILED);

    verify(transformer, times(1)).transform(any(RawReviewData.class), eq(mockprovider));
  }

  @Test
  void shouldCreateproviderIfNotExists() {
    // Given
    List<RawReviewData> rawReviews = createValidRawReviewDataList();
    List<Review> transformedReviews = createTransformedReviews();
    Provider newprovider =
        Provider.builder().id(2L).name("Booking").code(ProviderType.BOOKING).build();

    // Modify first review to use Booking provider
    rawReviews.get(0).setProvider(ProviderType.BOOKING.getDisplayName());
    rawReviews.get(1).setProvider(ProviderType.BOOKING.getDisplayName());

    when(validator.validate(any(RawReviewData.class))).thenReturn(ValidationResult.valid());
    when(providerRepository.findByName("Booking")).thenReturn(Optional.empty());
    when(providerRepository.save(any(Provider.class))).thenReturn(newprovider);
    when(transformer.transform(any(RawReviewData.class), eq(newprovider)))
        .thenReturn(transformedReviews.get(0), transformedReviews.get(1));
    when(reviewRepository.saveAll(anyList())).thenReturn(transformedReviews);

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getProcessedCount()).isEqualTo(2);
    assertThat(result.getValidCount()).isEqualTo(2);
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);

    verify(providerRepository)
        .save(
            argThat(
                provider ->
                    provider.getName().equals("Booking")
                        && provider.getCode() == ProviderType.BOOKING));
  }

  @Test
  void shouldHandleTransformationErrors() {
    // Given
    List<RawReviewData> rawReviews = createValidRawReviewDataList();

    when(validator.validate(any(RawReviewData.class))).thenReturn(ValidationResult.valid());
    when(providerRepository.findByName("Agoda")).thenReturn(Optional.of(mockprovider));
    when(transformer.transform(any(RawReviewData.class), eq(mockprovider)))
        .thenThrow(new RuntimeException("Transformation failed"));

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getProcessedCount()).isEqualTo(2);
    assertThat(result.getValidCount()).isEqualTo(0);
    assertThat(result.getInvalidCount()).isEqualTo(2);
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.FAILED);
    assertThat(result.getErrors()).hasSize(2);

    verify(reviewRepository, never()).saveAll(anyList());
  }

  @Test
  void shouldHandleDatabaseSaveErrors() {
    // Given
    List<RawReviewData> rawReviews = createValidRawReviewDataList();
    List<Review> transformedReviews = createTransformedReviews();

    when(validator.validate(any(RawReviewData.class))).thenReturn(ValidationResult.valid());
    when(providerRepository.findByName("Agoda")).thenReturn(Optional.of(mockprovider));
    when(transformer.transform(any(RawReviewData.class), eq(mockprovider)))
        .thenReturn(transformedReviews.get(0), transformedReviews.get(1));
    when(reviewRepository.saveAll(anyList()))
        .thenThrow(new RuntimeException("Database save failed"));

    // When & Then
    assertThatThrownBy(() -> reviewProcessingService.processBatch(rawReviews))
        .isInstanceOf(FileProcessingException.class)
        .hasMessageContaining("Failed to save review batch");
  }

  @Test
  void shouldProcessEmptyBatch() {
    // Given
    List<RawReviewData> emptyBatch = List.of();

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(emptyBatch);

    // Then
    assertThat(result.getProcessedCount()).isEqualTo(0);
    assertThat(result.getValidCount()).isEqualTo(0);
    assertThat(result.getInvalidCount()).isEqualTo(0);
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);

    verify(reviewRepository, never()).saveAll(anyList());
    verify(validator, never()).validate(any(RawReviewData.class));
  }

  @Test
  void shouldProcessLargeBatchInChunks() {
    // Given
    List<RawReviewData> largeBatch = createLargeRawReviewDataList(1000);
    List<Review> transformedReviews = createLargeTransformedReviews(1000);

    when(validator.validate(any(RawReviewData.class))).thenReturn(ValidationResult.valid());
    when(providerRepository.findByName("Agoda")).thenReturn(Optional.of(mockprovider));
    when(transformer.transform(any(RawReviewData.class), eq(mockprovider)))
        .thenAnswer(
            invocation -> {
              RawReviewData data = invocation.getArgument(0);
              return Review.builder()
                  .hotelId(data.getHotelId())
                  .hotelName(data.getHotelName())
                  .provider(mockprovider)
                  .hotelReviewId("review-" + data.getHotelId())
                  .build();
            });
    when(reviewRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(largeBatch);

    // Then
    assertThat(result.getProcessedCount()).isEqualTo(1000);
    assertThat(result.getValidCount()).isEqualTo(1000);
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);

    // Verify saveAll was called multiple times for chunking (assuming chunk size of 100)
    verify(reviewRepository, atLeast(10)).saveAll(anyList());
  }

  @Test
  @Transactional
  void shouldRollbackOnPartialFailure() {
    // Given
    List<RawReviewData> rawReviews = createValidRawReviewDataList();
    List<Review> transformedReviews = createTransformedReviews();

    when(validator.validate(any(RawReviewData.class))).thenReturn(ValidationResult.valid());
    when(providerRepository.findByName("Agoda")).thenReturn(Optional.of(mockprovider));
    when(transformer.transform(any(RawReviewData.class), eq(mockprovider)))
        .thenReturn(transformedReviews.get(0), transformedReviews.get(1));
    when(reviewRepository.saveAll(anyList()))
        .thenThrow(new RuntimeException("Database constraint violation"));

    // When & Then
    assertThatThrownBy(() -> reviewProcessingService.processBatch(rawReviews))
        .isInstanceOf(FileProcessingException.class);

    // Verify that no partial data is saved due to transaction rollback
    verify(reviewRepository).saveAll(anyList());
  }

  @Test
  void shouldHandleDuplicateReviews() {
    // Given
    List<RawReviewData> rawReviews = createValidRawReviewDataList();
    List<Review> transformedReviews = createTransformedReviews();

    when(validator.validate(any(RawReviewData.class))).thenReturn(ValidationResult.valid());
    when(providerRepository.findByName("Agoda")).thenReturn(Optional.of(mockprovider));
    when(transformer.transform(any(RawReviewData.class), eq(mockprovider)))
        .thenReturn(transformedReviews.get(0), transformedReviews.get(1));
    when(reviewRepository.existsByProviderReviewId(anyString()))
        .thenReturn(true, false); // First review exists, second doesn't
    when(reviewRepository.saveAll(anyList())).thenReturn(List.of(transformedReviews.get(1)));

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getProcessedCount()).isEqualTo(2);
    assertThat(result.getValidCount()).isEqualTo(1);
    assertThat(result.getDuplicateCount()).isEqualTo(1);
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
  }

  @Test
  void shouldTrackProcessingMetrics() {
    // Given
    List<RawReviewData> rawReviews = createMixedValidityRawReviewDataList();

    when(validator.validate(argThat(data -> data.getHotelId().equals(1))))
        .thenReturn(ValidationResult.valid());
    when(validator.validate(argThat(data -> data.getHotelId().equals(2))))
        .thenReturn(ValidationResult.invalid(List.of("Invalid data")));
    when(validator.validate(argThat(data -> data.getHotelId().equals(3))))
        .thenReturn(ValidationResult.valid());

    when(providerRepository.findByName("Agoda")).thenReturn(Optional.of(mockprovider));
    when(transformer.transform(any(RawReviewData.class), eq(mockprovider)))
        .thenReturn(createTransformedReviews().get(0));
    when(reviewRepository.saveAll(anyList()))
        .thenReturn(List.of(createTransformedReviews().get(0)));

    // When
    ReviewProcessingResult result = reviewProcessingService.processBatch(rawReviews);

    // Then
    assertThat(result.getProcessedCount()).isEqualTo(3);
    assertThat(result.getValidCount()).isEqualTo(2);
    assertThat(result.getInvalidCount()).isEqualTo(1);
    assertThat(result.getProcessingTimeMs()).isGreaterThan(0);
    assertThat(result.getStatus()).isEqualTo(ProcessingStatus.FAILED);
  }

  private List<RawReviewData> createValidRawReviewDataList() {
    return Arrays.asList(
        RawReviewData.builder()
            .hotelId(1)
            .provider("Agoda")
            .hotelName("Hotel 1")
            .comment(createValidComment())
            .lineNumber(1)
            .build(),
        RawReviewData.builder()
            .hotelId(2)
            .provider("Agoda")
            .hotelName("Hotel 2")
            .comment(createValidComment())
            .lineNumber(2)
            .build());
  }

  private List<RawReviewData> createMixedValidityRawReviewDataList() {
    return Arrays.asList(
        RawReviewData.builder()
            .hotelId(1)
            .provider("Agoda")
            .hotelName("Hotel 1")
            .comment(createValidComment())
            .lineNumber(1)
            .build(),
        RawReviewData.builder()
            .hotelId(2)
            .provider("Agoda")
            .hotelName("Hotel 2")
            .comment(createInvalidComment())
            .lineNumber(2)
            .build(),
        RawReviewData.builder()
            .hotelId(3)
            .provider("Agoda")
            .hotelName("Hotel 3")
            .comment(createValidComment())
            .lineNumber(3)
            .build());
  }

  private List<RawReviewData> createLargeRawReviewDataList(int size) {
    return java.util.stream.IntStream.range(1, size + 1)
        .mapToObj(
            i ->
                RawReviewData.builder()
                    .hotelId(i)
                    .provider("Agoda")
                    .hotelName("Hotel " + i)
                    .comment(createValidComment())
                    .lineNumber(i)
                    .build())
        .toList();
  }

  private List<Review> createTransformedReviews() {
    return Arrays.asList(
        Review.builder()
            .hotelId(1)
            .hotelName("Hotel 1")
            .provider(mockprovider)
            .hotelReviewId("review-1")
            .build(),
        Review.builder()
            .hotelId(2)
            .hotelName("Hotel 2")
            .provider(mockprovider)
            .hotelReviewId("review-2")
            .build());
  }

  private List<Review> createLargeTransformedReviews(int size) {
    return java.util.stream.IntStream.range(1, size + 1)
        .mapToObj(
            i ->
                Review.builder()
                    .hotelId(i)
                    .hotelName("Hotel " + i)
                    .provider(mockprovider)
                    .hotelReviewId("review-" + i)
                    .build())
        .toList();
  }

  private java.util.Map<String, Object> createValidComment() {
    return new java.util.HashMap<String, Object>() {
      {
        put("hotelReviewId", 123456);
        put("rating", 8.5);
        put("reviewComments", "Great hotel");
        put("reviewDate", "2025-04-10T05:37:00+07:00");
      }
    };
  }

  private java.util.Map<String, Object> createInvalidComment() {
    return new java.util.HashMap<String, Object>() {
      {
        put("hotelReviewId", 123456);
        put("rating", -1.0); // Invalid rating
        put("reviewComments", "Invalid review");
        put("reviewDate", "2025-04-10T05:37:00+07:00");
      }
    };
  }
}
