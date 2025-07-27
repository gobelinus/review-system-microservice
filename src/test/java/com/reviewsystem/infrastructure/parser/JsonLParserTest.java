package com.reviewsystem.infrastructure.parser;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.reviewsystem.infrastructure.parser.dto.RawReviewData;
import com.reviewsystem.presentation.exception.FileProcessingException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test-postgres")
class JsonLParserTest {

  private JsonLParser jsonLParser;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    jsonLParser = new JsonLParser();
  }

  @Test
  void shouldParseValidJsonLine() {
    // Given
    String validJsonLine =
        """
            {"hotelId": 10984, "provider": "Agoda", "hotelName": "Oscar Saigon Hotel",
             "comment": {"hotelReviewId": 948353737, "rating": 6.4, "reviewComments": "Good hotel",
             "reviewDate": "2025-04-10T05:37:00+07:00", "reviewerInfo": {"countryName": "India"}}}
            """;

    // When
    RawReviewData result = jsonLParser.parseLine(validJsonLine, 1);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getHotelId()).isEqualTo(10984);
    assertThat(result.getProvider()).isEqualTo("Agoda");
    assertThat(result.getHotelName()).isEqualTo("Oscar Saigon Hotel");
    assertThat(result.getLineNumber()).isEqualTo(1);
    assertThat(result.getRawJson()).isEqualTo(validJsonLine);
    assertThat(result.getComment()).isNotNull();
  }

  @Test
  void shouldReturnNullForMalformedJson() {
    // Given
    String malformedJson =
        """
            {"hotelId": 10984, "provider": "Agoda", "hotelName": "Oscar Saigon Hotel",
             "comment": {"hotelReviewId": 948353737, "rating": 6.4, "reviewComments": "Good hotel"
            """; // Missing closing braces

    // When
    RawReviewData result = jsonLParser.parseLine(malformedJson, 1);

    // Then
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullForEmptyLine() {
    // Given
    String emptyLine = "";

    // When
    RawReviewData result = jsonLParser.parseLine(emptyLine, 1);

    // Then
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullForWhitespaceOnlyLine() {
    // Given
    String whitespaceOnlyLine = "   \t\n  ";

    // When
    RawReviewData result = jsonLParser.parseLine(whitespaceOnlyLine, 1);

    // Then
    assertThat(result).isNull();
  }

  @Test
  void shouldParseFileWithValidLines() throws IOException {
    // Given
    Path testFile =
        createTestFile(
            "valid-reviews.jl",
            List.of(
                """
                {"hotelId": 1, "provider": "Agoda", "hotelName": "Hotel 1", "comment": {"rating": 8.0, "reviewComments": "Great"}}
                """,
                """
                {"hotelId": 2, "provider": "Booking", "hotelName": "Hotel 2", "comment": {"rating": 7.5, "reviewComments": "Good"}}
                """));

    // When
    List<RawReviewData> results = jsonLParser.parseFile(testFile.toString());

    // Then
    assertThat(results).hasSize(2);
    assertThat(results.get(0).getHotelId()).isEqualTo(1);
    assertThat(results.get(1).getHotelId()).isEqualTo(2);
    assertThat(results.get(0).getLineNumber()).isEqualTo(1);
    assertThat(results.get(1).getLineNumber()).isEqualTo(2);
  }

  @Test
  void shouldSkipMalformedLinesInFile() throws IOException {
    // Given
    Path testFile =
        createTestFile(
            "mixed-reviews.jl",
            List.of(
                """
                {"hotelId": 1, "provider": "Agoda", "hotelName": "Hotel 1", "comment": {"rating": 8.0}}
                """,
                """
                {"hotelId": 2, "provider": "Booking", "hotelName": "Hotel 2", "comment": {"rating": 7.5}
                """, // Malformed
                """
                {"hotelId": 3, "provider": "Expedia", "hotelName": "Hotel 3", "comment": {"rating": 9.0}}
                """));

    // When
    List<RawReviewData> results = jsonLParser.parseFile(testFile.toString());

    // Then
    assertThat(results).hasSize(2);
    assertThat(results.get(0).getHotelId()).isEqualTo(1);
    assertThat(results.get(1).getHotelId()).isEqualTo(3);
  }

  @Test
  void shouldStreamProcessLargeFile() throws IOException {
    // Given
    Path largeFile = createLargeTestFile(1000);
    AtomicInteger processedCount = new AtomicInteger(0);

    // When
    jsonLParser.streamProcessFile(
        largeFile.toString(),
        rawReviewData -> {
          processedCount.incrementAndGet();
          assertThat(rawReviewData).isNotNull();
          assertThat(rawReviewData.getHotelId()).isGreaterThan(0);
        });

    // Then
    assertThat(processedCount.get()).isEqualTo(1000);
  }

  @Test
  void shouldHandleMemoryEfficientProcessing() throws IOException {
    // Given
    Path largeFile = createLargeTestFile(10000);
    AtomicInteger processedCount = new AtomicInteger(0);
    long initialMemory = getUsedMemory();

    // When
    jsonLParser.streamProcessFile(
        largeFile.toString(),
        rawReviewData -> {
          processedCount.incrementAndGet();
        });

    // Then
    long finalMemory = getUsedMemory();
    long memoryIncrease = finalMemory - initialMemory;

    assertThat(processedCount.get()).isEqualTo(10000);
    // Memory increase should be reasonable (less than 50MB for 10k records)
    assertThat(memoryIncrease).isLessThan(50 * 1024 * 1024);
  }

  @Test
  void shouldHandleIOExceptionGracefully() {
    // Given
    String nonExistentFile = "/non/existent/file.jl";

    // When & Then
    assertThatThrownBy(() -> jsonLParser.parseFile(nonExistentFile))
        .isInstanceOf(FileProcessingException.class)
        .hasMessageContaining("Failed to process file");
  }

  @Test
  void shouldParseComplexNestedJson() {
    // Given
    String complexJson =
        """
            {
              "hotelId": 10984,
              "provider": "Agoda",
              "hotelName": "Oscar Saigon Hotel",
              "comment": {
                "hotelReviewId": 948353737,
                "rating": 6.4,
                "reviewComments": "Hotel room is basic and very small",
                "reviewDate": "2025-04-10T05:37:00+07:00",
                "reviewerInfo": {
                  "countryName": "India",
                  "displayMemberName": "********",
                  "reviewGroupName": "Solo traveler",
                  "lengthOfStay": 2
                }
              },
              "overallByproviders": [
                {
                  "providerId": 332,
                  "provider": "Agoda",
                  "overallScore": 7.9,
                  "reviewCount": 7070
                }
              ]
            }
            """;

    // When
    RawReviewData result = jsonLParser.parseLine(complexJson, 1);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getHotelId()).isEqualTo(10984);
    assertThat(result.getComment()).isNotNull();
    assertThat(result.getOverallByproviders()).isNotNull();
  }

  @Test
  void shouldHandleSpecialCharactersInJson() {
    // Given
    String jsonWithSpecialChars =
        """
            {"hotelId": 1, "provider": "Agoda", "hotelName": "Hôtel Café & Bar",
             "comment": {"rating": 8.0, "reviewComments": "Great place with café ☕ and bar 🍺"}}
            """;

    // When
    RawReviewData result = jsonLParser.parseLine(jsonWithSpecialChars, 1);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getHotelName()).isEqualTo("Hôtel Café & Bar");
  }

  @Test
  void shouldTrackLineNumbers() throws IOException {
    // Given
    Path testFile =
        createTestFile(
            "line-numbers.jl",
            List.of(
                """
                {"hotelId": 1, "provider": "Agoda"}
                """,
                "", // Empty line
                """
                {"hotelId": 2, "provider": "Booking"}
                """,
                """
                invalid json
                """,
                """
                {"hotelId": 3, "provider": "Expedia"}
                """));

    // When
    List<RawReviewData> results = jsonLParser.parseFile(testFile.toString());

    // Then
    assertThat(results).hasSize(3);
    assertThat(results.get(0).getLineNumber()).isEqualTo(1);
    assertThat(results.get(1).getLineNumber()).isEqualTo(3);
    assertThat(results.get(2).getLineNumber()).isEqualTo(5);
  }

  private Path createTestFile(String fileName, List<String> lines) throws IOException {
    Path file = tempDir.resolve(fileName);
    Files.write(file, lines);
    return file;
  }

  private Path createLargeTestFile(int recordCount) throws IOException {
    Path file = tempDir.resolve("large-test.jl");
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file))) {
      for (int i = 1; i <= recordCount; i++) {
        writer.println(
            String.format(
                """
                        {"hotelId": %d, "provider": "Agoda", "hotelName": "Hotel %d", "comment": {"rating": 8.0, "reviewComments": "Review %d"}}
                        """,
                i, i, i));
      }
    }
    return file;
  }

  private long getUsedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }
}
