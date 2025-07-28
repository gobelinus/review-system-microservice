package com.reviewsystem.presentation.controller;

import com.reviewsystem.application.dto.request.ReviewFilterRequest;
import com.reviewsystem.application.dto.response.ApiResponse;
import com.reviewsystem.application.dto.response.ReviewResponse;
import com.reviewsystem.application.service.ReviewQueryService;
import com.reviewsystem.common.enums.ProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Reviews", description = "Review management endpoints")
public class ReviewController {

  private final ReviewQueryService reviewQueryService;

  @GetMapping
  @Operation(summary = "Get all reviews with optional filtering and pagination")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved reviews"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getAllReviews(
      @Parameter(description = "Hotel ID to filter by") @RequestParam(required = false) @Positive
          Long hotelId,
      @Parameter(description = "Platform/Provider to filter by") @RequestParam(required = false)
          ProviderType platform,
      @Parameter(description = "Minimum rating (0.0 - 10.0)")
          @RequestParam(required = false)
          @DecimalMin(value = "0.0", message = "Minimum rating must be at least 0.0")
          @DecimalMax(value = "10.0", message = "Minimum rating must be at most 10.0")
          Double minRating,
      @Parameter(description = "Maximum rating (0.0 - 10.0)")
          @RequestParam(required = false)
          @DecimalMin(value = "0.0", message = "Maximum rating must be at least 0.0")
          @DecimalMax(value = "10.0", message = "Maximum rating must be at most 10.0")
          Double maxRating,
      @Parameter(description = "Reviewer country to filter by") @RequestParam(required = false)
          String reviewerCountry,
      @Parameter(description = "Hotel name to search for") @RequestParam(required = false)
          String hotelName,
      @Parameter(description = "Start date for review date range (YYYY-MM-DD)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @Parameter(description = "End date for review date range (YYYY-MM-DD)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @Parameter(description = "Minimum length of stay in days")
          @RequestParam(required = false)
          @Min(1)
          Integer minLengthOfStay,
      @Parameter(description = "Maximum length of stay in days")
          @RequestParam(required = false)
          @Min(1)
          Integer maxLengthOfStay,
      @Parameter(
              description =
                  "Pagination information. Default: page=0, size=20, sort by reviewDate desc")
          @PageableDefault(size = 20, sort = "reviewDate", direction = Sort.Direction.DESC)
          Pageable pageable) {

    log.info(
        "Fetching reviews with filters - hotelId: {}, platform: {}, minRating: {}, maxRating: {}, "
            + "reviewerCountry: {}, hotelName: {}, startDate: {}, endDate: {}, minLengthOfStay: {}, maxLengthOfStay: {}, "
            + "page: {}, size: {}",
        hotelId,
        platform,
        minRating,
        maxRating,
        reviewerCountry,
        hotelName,
        startDate,
        endDate,
        minLengthOfStay,
        maxLengthOfStay,
        pageable.getPageNumber(),
        pageable.getPageSize());

    ReviewFilterRequest filterRequest =
        ReviewFilterRequest.builder()
            .hotelId(hotelId)
            .platform(platform)
            .minRating(minRating)
            .maxRating(maxRating)
            .reviewerCountry(reviewerCountry)
            .hotelName(hotelName)
            .startDate(startDate)
            .endDate(endDate)
            .minLengthOfStay(minLengthOfStay)
            .maxLengthOfStay(maxLengthOfStay)
            .build();

    Page<ReviewResponse> reviews = reviewQueryService.getReviews(filterRequest, pageable);

    log.info(
        "Retrieved {} reviews out of {} total",
        reviews.getNumberOfElements(),
        reviews.getTotalElements());

    return ResponseEntity.ok(ApiResponse.success(reviews));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a specific review by ID")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved review"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Review not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid review ID"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<ApiResponse<ReviewResponse>> getReviewById(
      @Parameter(description = "Review ID", required = true) @PathVariable @Positive Long id) {

    log.info("Fetching review with ID: {}", id);

    ReviewResponse review = reviewQueryService.getReviewById(id);

    log.info("Successfully retrieved review with ID: {}", id);

    return ResponseEntity.ok(ApiResponse.success(review));
  }

  @GetMapping("/hotel/{hotelId}")
  @Operation(summary = "Get all reviews for a specific hotel")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved hotel reviews"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid hotel ID"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getReviewsByHotelId(
      @Parameter(description = "Hotel ID", required = true) @PathVariable @Positive Long hotelId,
      @Parameter(description = "Pagination information")
          @PageableDefault(size = 20, sort = "reviewDate", direction = Sort.Direction.DESC)
          Pageable pageable) {

    log.info(
        "Fetching reviews for hotel ID: {} with pagination: page={}, size={}",
        hotelId,
        pageable.getPageNumber(),
        pageable.getPageSize());

    Page<ReviewResponse> reviews = reviewQueryService.getReviewsByHotelId(hotelId, pageable);

    log.info("Retrieved {} reviews for hotel ID: {}", reviews.getNumberOfElements(), hotelId);

    return ResponseEntity.ok(ApiResponse.success(reviews));
  }

  @GetMapping("/statistics")
  @Operation(summary = "Get review statistics")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved statistics"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<ApiResponse<Map<String, Object>>> getReviewStatistics(
      @Parameter(description = "Hotel ID to get statistics for")
          @RequestParam(required = false)
          @Positive
          Long hotelId,
      @Parameter(description = "Platform to get statistics for") @RequestParam(required = false)
          ProviderType platform,
      @Parameter(description = "Start date for statistics range")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @Parameter(description = "End date for statistics range")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate) {

    log.info(
        "Fetching review statistics - hotelId: {}, platform: {}, startDate: {}, endDate: {}",
        hotelId,
        platform,
        startDate,
        endDate);

    ReviewFilterRequest filterRequest =
        ReviewFilterRequest.builder()
            .hotelId(hotelId)
            .platform(platform)
            .startDate(startDate)
            .endDate(endDate)
            .build();

    Map<String, Object> statistics = reviewQueryService.getReviewStatistics(filterRequest);

    log.info("Successfully retrieved review statistics");

    return ResponseEntity.ok(ApiResponse.success(statistics));
  }

  @GetMapping("/search")
  @Operation(summary = "Search reviews by keywords")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully searched reviews"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid search parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<ApiResponse<Page<ReviewResponse>>> searchReviews(
      @Parameter(description = "Search query for review content", required = true) @RequestParam
          String query,
      @Parameter(description = "Platform to search within") @RequestParam(required = false)
          ProviderType platform,
      @Parameter(description = "Minimum rating filter")
          @RequestParam(required = false)
          @DecimalMin(value = "0.0", message = "Minimum rating must be at least 0.0")
          @DecimalMax(value = "10.0", message = "Minimum rating must be at most 10.0")
          Double minRating,
      @Parameter(description = "Pagination information")
          @PageableDefault(size = 20, sort = "reviewDate", direction = Sort.Direction.DESC)
          Pageable pageable) {

    log.info(
        "Searching reviews with query: '{}', platform: {}, minRating: {}",
        query,
        platform,
        minRating);

    ReviewFilterRequest filterRequest =
        ReviewFilterRequest.builder()
            .searchQuery(query)
            .platform(platform)
            .minRating(minRating)
            .build();

    Page<ReviewResponse> reviews = reviewQueryService.searchReviews(filterRequest, pageable);

    log.info("Found {} reviews matching search query: '{}'", reviews.getTotalElements(), query);

    return ResponseEntity.ok(ApiResponse.success(reviews));
  }

  @GetMapping("/platforms/{platform}/statistics")
  @Operation(summary = "Get platform-specific review statistics")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved platform statistics"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid platform"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<ApiResponse<Map<String, Object>>> getPlatformStatistics(
      @Parameter(description = "Platform to get statistics for", required = true) @PathVariable
          ProviderType platform,
      @Parameter(description = "Start date for statistics range")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @Parameter(description = "End date for statistics range")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate) {

    log.info(
        "Fetching statistics for platform: {}, dateRange: {} to {}", platform, startDate, endDate);

    ReviewFilterRequest filterRequest =
        ReviewFilterRequest.builder()
            .platform(platform)
            .startDate(startDate)
            .endDate(endDate)
            .build();

    Map<String, Object> statistics = reviewQueryService.getPlatformStatistics(filterRequest);

    log.info("Successfully retrieved statistics for platform: {}", platform);

    return ResponseEntity.ok(ApiResponse.success(statistics));
  }
}
