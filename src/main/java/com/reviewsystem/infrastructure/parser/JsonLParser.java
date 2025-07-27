package com.reviewsystem.infrastructure.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.infrastructure.parser.dto.RawReviewData;
import com.reviewsystem.presentation.exception.FileProcessingException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonLParser {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Parses a single JSON line and returns RawReviewData */
  public RawReviewData parseLine(String jsonLine, int lineNumber) {
    if (jsonLine == null || jsonLine.trim().isEmpty()) {
      return null;
    }

    try {
      JsonNode rootNode = objectMapper.readTree(jsonLine.trim());

      return RawReviewData.builder()
          .hotelId(getIntegerValue(rootNode, "hotelId"))
          .provider(getStringValue(rootNode, "provider"))
          .hotelName(getStringValue(rootNode, "hotelName"))
          .comment(parseCommentSection(rootNode.get("comment")))
          .overallByproviders(parseOverallByproviders(rootNode.get("overallByproviders")))
          .lineNumber(lineNumber)
          .rawJson(jsonLine)
          .build();

    } catch (Exception e) {
      log.warn("Failed to parse JSON line {}: {}", lineNumber, e.getMessage());
      return null;
    }
  }

  /** Parses entire file and returns list of RawReviewData */
  public List<RawReviewData> parseFile(String filePath) {
    List<RawReviewData> results = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
      String line;
      int lineNumber = 1;

      while ((line = reader.readLine()) != null) {
        RawReviewData reviewData = parseLine(line, lineNumber);
        if (reviewData != null) {
          results.add(reviewData);
        }
        lineNumber++;
      }

      log.info("Successfully parsed {} reviews from file: {}", results.size(), filePath);
      return results;

    } catch (IOException e) {
      log.error("Failed to read file: {}", filePath, e);
      throw new FileProcessingException("Failed to process file: " + filePath, e);
    }
  }

  /** Stream processes file for memory-efficient handling of large files */
  public void streamProcessFile(String filePath, Consumer<RawReviewData> processor) {
    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
      String line;
      int lineNumber = 1;
      int processedCount = 0;

      while ((line = reader.readLine()) != null) {
        RawReviewData reviewData = parseLine(line, lineNumber);
        if (reviewData != null) {
          processor.accept(reviewData);
          processedCount++;

          // Log progress for large files
          if (processedCount % 1000 == 0) {
            log.debug("Processed {} reviews from file: {}", processedCount, filePath);
          }
        }
        lineNumber++;
      }

      log.info("Stream processed {} reviews from file: {}", processedCount, filePath);

    } catch (IOException e) {
      log.error("Failed to stream process file: {}", filePath, e);
      throw new FileProcessingException("Failed to stream process file: " + filePath, e);
    }
  }

  /**
   * Stream processes InputStream directly without creating temporary files Better for S3
   * integration and memory efficiency
   */
  public void processInputStream(InputStream inputStream, Consumer<RawReviewData> processor)
      throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 1;
      int processedCount = 0;
      int errorCount = 0;

      log.info("Starting to process input stream");

      while ((line = reader.readLine()) != null) {
        try {
          RawReviewData reviewData = parseLine(line, lineNumber);
          if (reviewData != null) {
            processor.accept(reviewData);
            processedCount++;

            // Log progress for large files
            if (processedCount % 1000 == 0) {
              log.debug("Processed {} reviews from stream (line {})", processedCount, lineNumber);
            }
          } else {
            errorCount++;
          }
        } catch (Exception e) {
          log.warn("Error processing line {}: {}", lineNumber, e.getMessage());
          errorCount++;
        }
        lineNumber++;
      }

      log.info(
          "Stream processing completed: {} reviews processed, {} errors, {} total lines",
          processedCount,
          errorCount,
          lineNumber - 1);

      if (errorCount > 0) {
        log.warn(
            "Processing completed with {} errors out of {} total lines",
            errorCount,
            lineNumber - 1);
      }

    } catch (IOException e) {
      log.error("Failed to process input stream", e);
      throw new FileProcessingException("Failed to process input stream", e);
    }
  }

  /** Processes InputStream and collects results in batches for efficient processing */
  public ProcessingResult processInputStreamInBatches(
      InputStream inputStream, int batchSize, Consumer<List<RawReviewData>> batchProcessor)
      throws IOException {

    List<RawReviewData> currentBatch = new ArrayList<>();
    ProcessingResult result = new ProcessingResult();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 1;

      log.info("Starting batch processing with batch size: {}", batchSize);

      while ((line = reader.readLine()) != null) {
        try {
          RawReviewData reviewData = parseLine(line, lineNumber);
          if (reviewData != null) {
            currentBatch.add(reviewData);
            result.incrementProcessed();

            // Process batch when it reaches the specified size
            if (currentBatch.size() >= batchSize) {
              processBatch(currentBatch, batchProcessor, result);
              currentBatch.clear();
            }
          } else {
            result.incrementErrors();
          }
        } catch (Exception e) {
          log.warn("Error processing line {}: {}", lineNumber, e.getMessage());
          result.incrementErrors();
          result.addError(new ProcessingError(lineNumber, line, e.getMessage()));
        }
        lineNumber++;
      }

      // Process remaining items in the last batch
      if (!currentBatch.isEmpty()) {
        processBatch(currentBatch, batchProcessor, result);
      }

      result.setTotalLines(lineNumber - 1);
      log.info("Batch processing completed: {}", result.getSummary());

      return result;

    } catch (IOException e) {
      log.error("Failed to process input stream in batches", e);
      throw new FileProcessingException("Failed to process input stream in batches", e);
    }
  }

  private void processBatch(
      List<RawReviewData> batch,
      Consumer<List<RawReviewData>> batchProcessor,
      ProcessingResult result) {
    try {
      batchProcessor.accept(new ArrayList<>(batch)); // Create defensive copy
      result.incrementBatchesProcessed();
      log.debug("Successfully processed batch of {} items", batch.size());
    } catch (Exception e) {
      log.error("Error processing batch of {} items: {}", batch.size(), e.getMessage(), e);
      result.incrementBatchErrors();
      result.addError(new ProcessingError(0, "BATCH_ERROR", e.getMessage()));
    }
  }

  /** Parses the comment section of the JSON */
  private HashMap<String, Object> parseCommentSection(JsonNode commentNode) {
    HashMap<String, Object> comment = new HashMap<>();

    if (commentNode != null && !commentNode.isNull()) {
      commentNode
          .fields()
          .forEachRemaining(
              entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                if (valueNode.isNull()) {
                  comment.put(key, null);
                } else if (valueNode.isTextual()) {
                  comment.put(key, valueNode.asText());
                } else if (valueNode.isNumber()) {
                  if (valueNode.isInt()) {
                    comment.put(key, valueNode.asInt());
                  } else {
                    comment.put(key, valueNode.asDouble());
                  }
                } else if (valueNode.isBoolean()) {
                  comment.put(key, valueNode.asBoolean());
                } else if (valueNode.isObject()) {
                  comment.put(key, parseNestedObject(valueNode));
                } else if (valueNode.isArray()) {
                  comment.put(key, parseArray(valueNode));
                }
              });
    }

    return comment;
  }

  /** Parses the overallByproviders array */
  private List<Object> parseOverallByproviders(JsonNode arrayNode) {
    List<Object> providers = new ArrayList<>();

    if (arrayNode != null && arrayNode.isArray()) {
      for (JsonNode providerNode : arrayNode) {
        providers.add(parseNestedObject(providerNode));
      }
    }

    return providers;
  }

  /** Recursively parses nested JSON objects */
  private HashMap<String, Object> parseNestedObject(JsonNode node) {
    HashMap<String, Object> result = new HashMap<>();

    if (node != null && node.isObject()) {
      node.fields()
          .forEachRemaining(
              entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                if (valueNode.isNull()) {
                  result.put(key, null);
                } else if (valueNode.isTextual()) {
                  result.put(key, valueNode.asText());
                } else if (valueNode.isNumber()) {
                  if (valueNode.isInt()) {
                    result.put(key, valueNode.asInt());
                  } else {
                    result.put(key, valueNode.asDouble());
                  }
                } else if (valueNode.isBoolean()) {
                  result.put(key, valueNode.asBoolean());
                } else if (valueNode.isObject()) {
                  result.put(key, parseNestedObject(valueNode));
                } else if (valueNode.isArray()) {
                  result.put(key, parseArray(valueNode));
                }
              });
    }

    return result;
  }

  /** Parses JSON arrays */
  private List<Object> parseArray(JsonNode arrayNode) {
    List<Object> result = new ArrayList<>();

    if (arrayNode != null && arrayNode.isArray()) {
      for (JsonNode element : arrayNode) {
        if (element.isTextual()) {
          result.add(element.asText());
        } else if (element.isNumber()) {
          if (element.isInt()) {
            result.add(element.asInt());
          } else {
            result.add(element.asDouble());
          }
        } else if (element.isBoolean()) {
          result.add(element.asBoolean());
        } else if (element.isObject()) {
          result.add(parseNestedObject(element));
        } else if (element.isArray()) {
          result.add(parseArray(element));
        }
      }
    }

    return result;
  }

  /** Safely extracts string value from JSON node */
  private String getStringValue(JsonNode rootNode, String fieldName) {
    JsonNode node = rootNode.get(fieldName);
    return node != null && !node.isNull() ? node.asText() : null;
  }

  /** Safely extracts integer value from JSON node */
  private Integer getIntegerValue(JsonNode rootNode, String fieldName) {
    JsonNode node = rootNode.get(fieldName);
    return node != null && !node.isNull() ? node.asInt() : null;
  }

  /** Represents the result of processing a file or stream */
  public static class ProcessingResult {
    private int processedCount = 0;
    private int errorCount = 0;
    private int totalLines = 0;
    private int batchesProcessed = 0;
    private int batchErrors = 0;
    private List<ProcessingError> errors = new ArrayList<>();

    public void incrementProcessed() {
      processedCount++;
    }

    public void incrementErrors() {
      errorCount++;
    }

    public void incrementBatchesProcessed() {
      batchesProcessed++;
    }

    public void incrementBatchErrors() {
      batchErrors++;
    }

    public void addError(ProcessingError error) {
      errors.add(error);
    }

    // Getters
    public int getProcessedCount() {
      return processedCount;
    }

    public int getErrorCount() {
      return errorCount;
    }

    public int getTotalLines() {
      return totalLines;
    }

    public int getBatchesProcessed() {
      return batchesProcessed;
    }

    public int getBatchErrors() {
      return batchErrors;
    }

    public List<ProcessingError> getErrors() {
      return errors;
    }

    public void setTotalLines(int totalLines) {
      this.totalLines = totalLines;
    }

    public String getSummary() {
      return String.format(
          "Processed: %d, Errors: %d, Total Lines: %d, Batches: %d, Batch Errors: %d",
          processedCount, errorCount, totalLines, batchesProcessed, batchErrors);
    }

    public boolean hasErrors() {
      return errorCount > 0 || batchErrors > 0;
    }

    public double getSuccessRate() {
      return totalLines > 0 ? (double) processedCount / totalLines * 100 : 0;
    }
  }

  /** Represents a processing error with context information */
  public static class ProcessingError {
    private final int lineNumber;
    private final String line;
    private final String error;
    private final LocalDateTime timestamp;

    public ProcessingError(int lineNumber, String line, String error) {
      this.lineNumber = lineNumber;
      this.line = line != null && line.length() > 100 ? line.substring(0, 100) + "..." : line;
      this.error = error;
      this.timestamp = LocalDateTime.now();
    }

    // Getters
    public int getLineNumber() {
      return lineNumber;
    }

    public String getLine() {
      return line;
    }

    public String getError() {
      return error;
    }

    public LocalDateTime getTimestamp() {
      return timestamp;
    }

    @Override
    public String toString() {
      return String.format("Line %d: %s", lineNumber, error);
    }
  }

  // ReviewProcessingService.java - Add this static inner class at the end of the class

  /** Tracks the results of processing a single file including all error types and statistics */
  public static class FileProcessingResult {
    private final String fileKey;
    private final String fileETag;
    private final long fileSize;
    private Long processedFileId;
    private ProcessingStatus status = ProcessingStatus.IN_PROGRESS;

    // Statistics counters
    private int savedReviews = 0;
    private int validationErrors = 0;
    private int transformationErrors = 0;
    private int processingErrors = 0;
    private int databaseErrors = 0;

    // Error tracking
    private List<String> errors = new ArrayList<>();
    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;

    // Timing information
    private long processingDurationMs;

    public FileProcessingResult(String fileKey, String fileETag, long fileSize) {
      this.fileKey = fileKey;
      this.fileETag = fileETag;
      this.fileSize = fileSize;
    }

    // Increment methods for tracking different types of operations
    public void incrementSavedReviews(int count) {
      savedReviews += count;
    }

    public void incrementValidationErrors() {
      validationErrors++;
    }

    public void incrementTransformationErrors() {
      transformationErrors++;
    }

    public void incrementProcessingErrors() {
      processingErrors++;
    }

    public void incrementDatabaseErrors() {
      databaseErrors++;
    }

    public void addError(String error) {
      errors.add(error);
      // Keep only the last 50 errors to prevent memory issues
      if (errors.size() > 50) {
        errors.remove(0);
      }
    }

    public int getTotalErrors() {
      return validationErrors + transformationErrors + processingErrors + databaseErrors;
    }

    public void updateFromParsingResult(JsonLParser.ProcessingResult parsingResult) {
      this.endTime = LocalDateTime.now();
      this.processingDurationMs = java.time.Duration.between(startTime, endTime).toMillis();
    }

    public String getSummary() {
      return String.format(
          "File: %s, Saved: %d, Errors: %d (V:%d, T:%d, P:%d, D:%d), Duration: %dms",
          fileKey,
          savedReviews,
          getTotalErrors(),
          validationErrors,
          transformationErrors,
          processingErrors,
          databaseErrors,
          processingDurationMs);
    }

    public String getErrorSummary() {
      if (errors.isEmpty()) {
        return null;
      }
      return errors.size() > 10
          ? String.join("; ", errors.subList(0, 10)) + "... (+" + (errors.size() - 10) + " more)"
          : String.join("; ", errors);
    }

    public boolean isSuccessful() {
      return status == ProcessingStatus.COMPLETED && savedReviews > 0;
    }

    public double getErrorRate() {
      int totalOperations = savedReviews + getTotalErrors();
      return totalOperations > 0 ? (double) getTotalErrors() / totalOperations * 100 : 0;
    }

    // Getters and setters
    public String getFileKey() {
      return fileKey;
    }

    public String getFileETag() {
      return fileETag;
    }

    public long getFileSize() {
      return fileSize;
    }

    public ProcessingStatus getStatus() {
      return status;
    }

    public void setStatus(ProcessingStatus status) {
      this.status = status;
    }

    public int getSavedReviews() {
      return savedReviews;
    }

    public Long getProcessedFileId() {
      return processedFileId;
    }

    public void setProcessedFileId(Long processedFileId) {
      this.processedFileId = processedFileId;
    }

    public int getValidationErrors() {
      return validationErrors;
    }

    public int getTransformationErrors() {
      return transformationErrors;
    }

    public int getProcessingErrors() {
      return processingErrors;
    }

    public int getDatabaseErrors() {
      return databaseErrors;
    }

    public List<String> getErrors() {
      return errors;
    }

    public LocalDateTime getStartTime() {
      return startTime;
    }

    public LocalDateTime getEndTime() {
      return endTime;
    }

    public long getProcessingDurationMs() {
      return processingDurationMs;
    }
  }
}
