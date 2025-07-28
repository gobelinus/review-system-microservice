package com.reviewsystem.presentation.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewsystem.application.dto.request.ReviewFilterRequest;
import com.reviewsystem.application.dto.response.ReviewResponse;
import com.reviewsystem.application.service.ReviewQueryService;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.presentation.exception.ReviewNotFoundException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(ReviewController.class)
@ActiveProfiles("test-postgres")
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private ReviewQueryService reviewQueryService;

  @Autowired private ObjectMapper objectMapper;

  private ReviewResponse sampleReview;
  private List<ReviewResponse> sampleReviews;

  @BeforeEach
  void setUp() {
    sampleReview =
        ReviewResponse.builder()
            .id(1L)
            .hotelId(10984L)
            .hotelName("Oscar Saigon Hotel")
            .platform(ProviderType.AGODA)
            .rating(6.4)
            .ratingText("Good")
            .reviewTitle("Perfect location and safe but hotel under renovation")
            .reviewComments("Hotel room is basic and very small...")
            .reviewDate(LocalDateTime.of(2025, 4, 10, 5, 37, 0))
            .reviewerCountry("India")
            .roomTypeName("Premium Deluxe Double Room")
            .lengthOfStay(2)
            .build();

    ReviewResponse anotherReview =
        ReviewResponse.builder()
            .id(2L)
            .hotelId(20985L)
            .hotelName("Luxury Hotel")
            .platform(ProviderType.BOOKING)
            .rating(8.5)
            .ratingText("Excellent")
            .reviewTitle("Amazing experience")
            .reviewComments("Great service and location")
            .reviewDate(LocalDateTime.of(2025, 4, 15, 10, 30, 0))
            .reviewerCountry("USA")
            .roomTypeName("Deluxe Suite")
            .lengthOfStay(3)
            .build();

    sampleReviews = Arrays.asList(sampleReview, anotherReview);
  }

  @Test
  void getAllReviews_ShouldReturnPagedReviews_WhenNoFiltersApplied() throws Exception {
    // Given
    Pageable pageable = PageRequest.of(0, 20, Sort.by("reviewDate").descending());
    Page<ReviewResponse> reviewPage = new PageImpl<>(sampleReviews, pageable, sampleReviews.size());

    when(reviewQueryService.getReviews(any(ReviewFilterRequest.class), eq(pageable)))
        .thenReturn(reviewPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.content[0].id", is(1)))
        .andExpect(jsonPath("$.data.content[0].hotelName", is("Oscar Saigon Hotel")))
        .andExpect(jsonPath("$.data.content[0].platform", is("AGODA")))
        .andExpect(jsonPath("$.data.content[0].rating", is(6.4)))
        .andExpect(jsonPath("$.data.totalElements", is(2)))
        .andExpect(jsonPath("$.data.totalPages", is(1)))
        .andExpect(jsonPath("$.data.first", is(true)))
        .andExpect(jsonPath("$.data.last", is(true)));

    verify(reviewQueryService).getReviews(any(ReviewFilterRequest.class), eq(pageable));
  }

  @Test
  void getAllReviews_ShouldReturnFilteredReviews_WhenFiltersApplied() throws Exception {
    // Given
    List<ReviewResponse> filteredReviews = Arrays.asList(sampleReview);
    Pageable pageable = PageRequest.of(0, 20, Sort.by("reviewDate").descending());
    Page<ReviewResponse> reviewPage = new PageImpl<>(filteredReviews, pageable, 1);

    when(reviewQueryService.getReviews(any(ReviewFilterRequest.class), eq(pageable)))
        .thenReturn(reviewPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/reviews")
                .param("hotelId", "10984")
                .param("platform", "AGODA")
                .param("minRating", "6.0")
                .param("maxRating", "7.0")
                .param("reviewerCountry", "India")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].hotelId", is(10984)))
        .andExpect(jsonPath("$.data.content[0].platform", is("AGODA")))
        .andExpect(jsonPath("$.data.totalElements", is(1)));

    verify(reviewQueryService).getReviews(any(ReviewFilterRequest.class), eq(pageable));
  }

  @Test
  void getAllReviews_ShouldReturnCustomPagedReviews_WhenPaginationParametersProvided()
      throws Exception {
    // Given
    Pageable pageable = PageRequest.of(1, 10, Sort.by("rating").ascending());
    Page<ReviewResponse> reviewPage = new PageImpl<>(sampleReviews, pageable, 25);

    when(reviewQueryService.getReviews(any(ReviewFilterRequest.class), eq(pageable)))
        .thenReturn(reviewPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/reviews")
                .param("page", "1")
                .param("size", "10")
                .param("sort", "rating,asc")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.totalElements", is(25)))
        .andExpect(jsonPath("$.data.number", is(1)))
        .andExpect(jsonPath("$.data.size", is(10)));

    verify(reviewQueryService).getReviews(any(ReviewFilterRequest.class), eq(pageable));
  }

  @Test
  void getAllReviews_ShouldReturnCustomSortedReviews_WhenSortParametersProvided() throws Exception {
    // Given
    Pageable pageable =
        PageRequest.of(
            0, 20, Sort.by("rating").descending().and(Sort.by("reviewDate").ascending()));
    Page<ReviewResponse> reviewPage = new PageImpl<>(sampleReviews, pageable, sampleReviews.size());

    when(reviewQueryService.getReviews(any(ReviewFilterRequest.class), eq(pageable)))
        .thenReturn(reviewPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/reviews")
                .param("sort", "rating,desc")
                .param("sort", "reviewDate,asc")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.content", hasSize(2)));

    verify(reviewQueryService).getReviews(any(ReviewFilterRequest.class), eq(pageable));
  }

  @Test
  void getReviewById_ShouldReturnReview_WhenReviewExists() throws Exception {
    // Given
    when(reviewQueryService.getReviewById(1L)).thenReturn(sampleReview);

    // When & Then
    mockMvc
        .perform(get("/api/v1/reviews/1").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.id", is(1)))
        .andExpect(jsonPath("$.data.hotelName", is("Oscar Saigon Hotel")))
        .andExpect(jsonPath("$.data.platform", is("AGODA")))
        .andExpect(jsonPath("$.data.rating", is(6.4)));

    verify(reviewQueryService).getReviewById(1L);
  }

  @Test
  void getReviewById_ShouldReturnNotFound_WhenReviewDoesNotExist() throws Exception {
    // Given
    when(reviewQueryService.getReviewById(999L))
        .thenThrow(new ReviewNotFoundException("Review not found with id: 999"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/reviews/999").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.error.code", is("REVIEW_NOT_FOUND")))
        .andExpect(jsonPath("$.error.message", containsString("Review not found with id: 999")));

    verify(reviewQueryService).getReviewById(999L);
  }

  @Test
  void getReviewsByHotelId_ShouldReturnHotelReviews_WhenHotelExists() throws Exception {
    // Given
    List<ReviewResponse> hotelReviews = Arrays.asList(sampleReview);
    Pageable pageable = PageRequest.of(0, 20, Sort.by("reviewDate").descending());
    Page<ReviewResponse> reviewPage = new PageImpl<>(hotelReviews, pageable, hotelReviews.size());

    when(reviewQueryService.getReviewsByHotelId(eq(10984L), eq(pageable))).thenReturn(reviewPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/reviews/hotel/10984").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].hotelId", is(10984)))
        .andExpect(jsonPath("$.data.totalElements", is(1)));

    verify(reviewQueryService).getReviewsByHotelId(10984L, pageable);
  }

  @Test
  void getAllReviews_ShouldReturnBadRequest_WhenInvalidParametersProvided() throws Exception {
    // When & Then
    mockMvc
        .perform(
            get("/api/v1/reviews")
                .param("minRating", "invalid")
                .param("page", "-1")
                .param("size", "0")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
  }

  @Test
  void getAllReviews_ShouldReturnBadRequest_WhenInvalidDateRangeProvided() throws Exception {
    // When & Then
    mockMvc
        .perform(
            get("/api/v1/reviews")
                .param("startDate", "2025-05-01")
                .param("endDate", "2025-04-01")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
        .andExpect(
            jsonPath("$.error.message", containsString("End date must be after start date")));
  }

  @Test
  void getAllReviews_ShouldHandleServiceException_WhenInternalErrorOccurs() throws Exception {
    // Given
    when(reviewQueryService.getReviews(any(ReviewFilterRequest.class), any(Pageable.class)))
        .thenThrow(new RuntimeException("Database connection failed"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.error.code", is("INTERNAL_SERVER_ERROR")))
        .andExpect(jsonPath("$.error.message", is("An unexpected error occurred")));

    verify(reviewQueryService).getReviews(any(ReviewFilterRequest.class), any(Pageable.class));
  }

  @Test
  void getReviewStatistics_ShouldReturnStatistics_WhenValidRequest() throws Exception {
    // Given
    // This would be handled by a separate statistics endpoint
    // Just testing the endpoint exists and returns proper structure

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/reviews/statistics")
                .param("hotelId", "10984")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)));
  }

  @Test
  void getAllReviews_ShouldApplyDefaultSorting_WhenNoSortProvided() throws Exception {
    // Given
    Pageable defaultPageable = PageRequest.of(0, 20, Sort.by("reviewDate").descending());
    Page<ReviewResponse> reviewPage =
        new PageImpl<>(sampleReviews, defaultPageable, sampleReviews.size());

    when(reviewQueryService.getReviews(any(ReviewFilterRequest.class), eq(defaultPageable)))
        .thenReturn(reviewPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.content", hasSize(2)));

    verify(reviewQueryService).getReviews(any(ReviewFilterRequest.class), eq(defaultPageable));
  }
}
