package com.reviewsystem.application.service;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.Provider;
import com.reviewsystem.domain.entity.Review;
import com.reviewsystem.domain.repository.ProviderRepository;
import com.reviewsystem.domain.repository.ReviewRepository;
import com.reviewsystem.domain.service.FileTrackingService;
import com.reviewsystem.infrastructure.aws.S3Service;
import com.reviewsystem.infrastructure.monitoring.ProcessingMetrics;
import com.reviewsystem.infrastructure.parser.JsonLParser;
import com.reviewsystem.infrastructure.parser.ReviewDataTransformer;
import com.reviewsystem.infrastructure.parser.ReviewDataValidator;
import com.reviewsystem.infrastructure.parser.dto.RawReviewData;
import com.reviewsystem.infrastructure.parser.dto.ValidationResult;
import com.reviewsystem.presentation.exception.FileProcessingException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewProcessingService {

  private final S3Service s3Service;
  private final JsonLParser jsonLParser;
  private final ReviewDataValidator validator;
  private final ReviewDataTransformer transformer;
  private final ReviewRepository reviewRepository;
  private final ProviderRepository providerRepository;
  private final FileTrackingService fileTrackingService;
  private final ProcessingMetrics processingMetrics;

  private static final int BATCH_SIZE = 100;
  private final Map<String, Provider> providerCache = new ConcurrentHashMap<>();
  private static final int MAX_ERROR_SIZE = 50;

  /** Processes a batch of raw review data */
  @Transactional(rollbackFor = Exception.class)
  public ReviewProcessingResult processBatch(List<RawReviewData> rawReviews) {
    long startTime = System.currentTimeMillis();

    if (rawReviews == null || rawReviews.isEmpty()) {
      return ReviewProcessingResult.builder()
          .processedCount(0)
          .validCount(0)
          .invalidCount(0)
          .duplicateCount(0)
          .status(ProcessingStatus.COMPLETED)
          .processingTimeMs(0)
          .errors(List.of())
          .build();
    }

    log.info("Processing batch of {} reviews", rawReviews.size());

    List<String> errors = new ArrayList<>();
    List<Review> validReviews = new ArrayList<>();
    int validCount = 0;
    int invalidCount = 0;
    int duplicateCount = 0;

    for (RawReviewData rawReview : rawReviews) {
      try {
        // Validate the review data
        ValidationResult validationResult = validator.validate(rawReview);

        if (!validationResult.isValid()) {
          invalidCount++;
          errors.addAll(
              validationResult.getErrors().stream()
                  .map(error -> String.format("Line %d: %s", rawReview.getLineNumber(), error))
                  .collect(Collectors.toList()));
          continue;
        }

        // Get or create provider
        Provider provider = getOrCreateprovider(rawReview.getProvider());

        // Transform to domain entity
        Review review = transformer.transform(rawReview, provider);

        // Check for duplicates
        if (isDuplicate(review)) {
          duplicateCount++;
          log.debug("Skipping duplicate review: {}", review.getProviderExternalId());
          continue;
        }

        validReviews.add(review);
        validCount++;

      } catch (Exception e) {
        invalidCount++;
        errors.add(
            String.format(
                "Line %d: Processing failed - %s", rawReview.getLineNumber(), e.getMessage()));
        log.error("Failed to process review on line {}", rawReview.getLineNumber(), e);
      }
    }

    // Save valid reviews in batches
    try {
      saveInBatches(validReviews);
    } catch (Exception e) {
      log.error("Failed to save review batch", e);
      throw new FileProcessingException("Failed to save review batch", e);
    }

    long processingTime = System.currentTimeMillis() - startTime;
    ProcessingStatus status =
        determineStatus(rawReviews.size(), validCount, invalidCount, errors.isEmpty());

    ReviewProcessingResult result =
        ReviewProcessingResult.builder()
            .processedCount(rawReviews.size())
            .validCount(validCount)
            .invalidCount(invalidCount)
            .duplicateCount(duplicateCount)
            .status(status)
            .processingTimeMs(processingTime)
            .errors(errors)
            .build();

    log.info("Batch processing completed: {}", result);
    return result;
  }

  /** Gets existing provider or creates new one */
  private Provider getOrCreateprovider(String providerName) {
    return providerCache.computeIfAbsent(
        providerName,
        name -> {
          return providerRepository
              .findByName(name)
              .orElseGet(
                  () -> {
                    Provider newprovider =
                        Provider.builder()
                            .name(name)
                            .code(ProviderType.fromString(name))
                            .active(true)
                            .build();

                    Provider savedProvider = providerRepository.save(newprovider);
                    log.info("Created new provider: {}", savedProvider.getName());
                    return savedProvider;
                  });
        });
  }

  /** Checks if review is duplicate */
  private boolean isDuplicate(Review review) {
    return reviewRepository.existsByProviderExternalId(review.getProviderExternalId());
  }

  /** Saves reviews in batches for better performance */
  private void saveInBatches(List<Review> reviews) {
    if (reviews.isEmpty()) {
      return;
    }

    List<List<Review>> batches = createBatches(reviews, BATCH_SIZE);

    for (List<Review> batch : batches) {
      try {
        reviewRepository.saveAll(batch);
        log.debug("Saved batch of {} reviews", batch.size());
      } catch (Exception e) {
        log.error("Failed to save batch of {} reviews", batch.size(), e);
        throw e;
      }
    }

    log.info("Successfully saved {} reviews in {} batches", reviews.size(), batches.size());
  }

  /** Creates batches from list */
  private <T> List<List<T>> createBatches(List<T> items, int batchSize) {
    List<List<T>> batches = new ArrayList<>();

    for (int i = 0; i < items.size(); i += batchSize) {
      int endIndex = Math.min(i + batchSize, items.size());
      batches.add(items.subList(i, endIndex));
    }

    return batches;
  }

  /** Determines processing status based on results */
  private ProcessingStatus determineStatus(
      int totalCount, int validCount, int invalidCount, boolean noErrors) {
    if (totalCount == 0) {
      return ProcessingStatus.COMPLETED;
    }

    if (validCount == 0) {
      return ProcessingStatus.FAILED;
    }

    if (invalidCount > 0 || !noErrors) {
      return ProcessingStatus.FAILED;
    }

    return ProcessingStatus.COMPLETED;
  }

  /** Processes reviews asynchronously for large batches */
  public ReviewProcessingResult processLargeBatch(List<RawReviewData> rawReviews) {
    log.info("Processing large batch of {} reviews", rawReviews.size());

    // For very large batches, process in smaller chunks
    if (rawReviews.size() > 10000) {
      return processInChunks(rawReviews);
    }

    return processBatch(rawReviews);
  }

  /** Processes large batches in chunks */
  private ReviewProcessingResult processInChunks(List<RawReviewData> rawReviews) {
    int chunkSize = 1000;
    List<List<RawReviewData>> chunks = createBatches(rawReviews, chunkSize);

    int totalProcessed = 0;
    int totalValid = 0;
    int totalInvalid = 0;
    int totalDuplicates = 0;
    List<String> allErrors = new ArrayList<>();
    long totalProcessingTime = 0;

    for (int i = 0; i < chunks.size(); i++) {
      List<RawReviewData> chunk = chunks.get(i);
      log.info("Processing chunk {} of {} ({} reviews)", i + 1, chunks.size(), chunk.size());

      ReviewProcessingResult chunkResult = processBatch(chunk);

      totalProcessed += chunkResult.getProcessedCount();
      totalValid += chunkResult.getValidCount();
      totalInvalid += chunkResult.getInvalidCount();
      totalDuplicates += chunkResult.getDuplicateCount();
      allErrors.addAll(chunkResult.getErrors());
      totalProcessingTime += chunkResult.getProcessingTimeMs();

      // Log progress
      if ((i + 1) % 10 == 0) {
        log.info("Processed {} of {} chunks", i + 1, chunks.size());
      }
    }

    ProcessingStatus finalStatus =
        determineStatus(totalProcessed, totalValid, totalInvalid, allErrors.isEmpty());

    return ReviewProcessingResult.builder()
        .processedCount(totalProcessed)
        .validCount(totalValid)
        .invalidCount(totalInvalid)
        .duplicateCount(totalDuplicates)
        .status(finalStatus)
        .processingTimeMs(totalProcessingTime)
        .errors(allErrors)
        .build();
  }

  /** Clears provider cache (useful for testing) */
  public void clearProviderCache() {
    providerCache.clear();
    log.debug("provider cache cleared");
  }

  /** Gets processing statistics */
  public ProcessingStatistics getProcessingStatistics() {
    long totalReviews = reviewRepository.count();
    long totalProviders = providerRepository.count();

    return ProcessingStatistics.builder()
        .totalReviews(totalReviews)
        .totalProviders(totalProviders)
        .cacheSize(providerCache.size())
        .build();
  }

  /**
   * Processes a single file from S3 and stores the reviews in the database.
   *
   * @param fileKey The S3 file key to process
   * @return Number of reviews processed from the file
   */
  @Transactional
  public Long processFile(String fileKey) {
    log.info("Starting processing of file: {}", fileKey);

    // Mark file as being processed
    fileTrackingService.markProcessingStartedByS3Key(fileKey);
    processingMetrics.recordFileProcessingStart(1);

    AtomicLong processedCount = new AtomicLong(0);
    AtomicLong errorCount = new AtomicLong(0);

    try {
      // Validate file exists
      if (!s3Service.fileExists(fileKey)) {
        throw new RuntimeException("File does not exist in S3: " + fileKey);
      }

      // Download and process file
      try (InputStream inputStream = s3Service.downloadFile(fileKey)) {

        // Parse file in batches
        jsonLParser.processInputStreamInBatches(
            inputStream,
            BATCH_SIZE,
            reviewBatch -> {
              try {
                ReviewProcessingResult batchProcessed = processBatch(reviewBatch);
                processedCount.addAndGet(batchProcessed.getProcessedCount());

                log.debug("Processed batch of {} reviews from file: {}", processedCount, fileKey);

                // Check if too many errors occurred
                if (errorCount.get() > MAX_ERROR_SIZE) {
                  log.error(
                      "Too many errors ({}) processing file: {}, stopping processing",
                      errorCount.get(),
                      fileKey);
                  throw new RuntimeException("Too many validation errors in file: " + fileKey);
                }

              } catch (Exception e) {
                log.error("Error processing batch from file: {}", fileKey, e);
                throw new RuntimeException("Batch processing failed for file: " + fileKey, e);
              }
            });
      }

      // Mark file as completed
      fileTrackingService.markProcessingCompleted(fileKey, (int) processedCount.get(), 0);
      processingMetrics.recordFileProcessingComplete(processedCount.get());

      log.info(
          "Successfully processed file: {} - {} reviews processed, {} errors",
          fileKey,
          processedCount.get(),
          errorCount.get());

      return processedCount.get();

    } catch (Exception e) {
      log.error("Error processing file: {}", fileKey, e);

      // Mark file as failed
      fileTrackingService.markProcessingFailed(fileKey, e.getMessage());
      processingMetrics.recordFileProcessingError(fileKey, e);

      throw new RuntimeException("Failed to process file: " + fileKey, e);
    }
  }
}
