package com.reviewsystem.presentation.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewsystem.application.dto.request.ProcessingTriggerRequest;
import com.reviewsystem.application.dto.response.ProcessingStatusResponse;
import com.reviewsystem.application.service.ProcessingOrchestrationService;
import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(AdminController.class)
@ActiveProfiles("test-postgres")
class AdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private ProcessingOrchestrationService processingOrchestrationService;

  @Autowired private ObjectMapper objectMapper;

  private ProcessingStatusResponse sampleProcessingStatus;
  private ProcessingTriggerRequest triggerRequest;

  @BeforeEach
  void setUp() {
    sampleProcessingStatus =
        ProcessingStatusResponse.builder()
            .id("processing-001")
            .status(ProcessingStatus.COMPLETED)
            .provider(ProviderType.AGODA)
            .totalFiles(5)
            .processedFiles(5)
            .totalReviews(1250)
            .processedReviews(1200)
            .failedReviews(50)
            .startTime(LocalDateTime.of(2025, 4, 15, 10, 0, 0))
            .endTime(LocalDateTime.of(2025, 4, 15, 10, 15, 30))
            .errorMessage(null)
            .build();

    triggerRequest =
        ProcessingTriggerRequest.builder()
            .provider(ProviderType.AGODA)
            .forceReprocess(false)
            .maxFiles(10)
            .build();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void triggerProcessing_ShouldStartProcessing_WhenValidRequest() throws Exception {
    // Given
    when(processingOrchestrationService.triggerProcessing(any(ProcessingTriggerRequest.class)))
        .thenReturn("processing-001");

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/admin/processing/trigger")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(triggerRequest)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.processingId", is("processing-001")))
        .andExpect(jsonPath("$.message", is("Processing triggered successfully")));

    verify(processingOrchestrationService).triggerProcessing(any(ProcessingTriggerRequest.class));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void triggerProcessing_ShouldReturnBadRequest_WhenInvalidRequest() throws Exception {
    // Given
    ProcessingTriggerRequest invalidRequest =
        ProcessingTriggerRequest.builder()
            .provider(null) // Invalid - null provider
            .maxFiles(-1) // Invalid - negative maxFiles
            .build();

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/admin/processing/trigger")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));

    verify(processingOrchestrationService, never()).triggerProcessing(any());
  }

  @Test
  void triggerProcessing_ShouldReturnUnauthorized_WhenNotAuthenticated() throws Exception {
    // When & Then
    mockMvc
        .perform(
            post("/api/v1/admin/processing/trigger")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(triggerRequest)))
        .andExpect(status().isUnauthorized());

    verify(processingOrchestrationService, never()).triggerProcessing(any());
  }

  @Test
  @WithMockUser(roles = "USER")
  void triggerProcessing_ShouldReturnForbidden_WhenInsufficientRole() throws Exception {
    // When & Then
    mockMvc
        .perform(
            post("/api/v1/admin/processing/trigger")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(triggerRequest)))
        .andExpect(status().isForbidden());

    verify(processingOrchestrationService, never()).triggerProcessing(any());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getProcessingStatus_ShouldReturnStatus_WhenProcessingExists() throws Exception {
    // Given
    when(processingOrchestrationService.getProcessingStatus("processing-001"))
        .thenReturn(sampleProcessingStatus);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/admin/processing/status/processing-001")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.id", is("processing-001")))
        .andExpect(jsonPath("$.data.status", is("COMPLETED")))
        .andExpect(jsonPath("$.data.provider", is("AGODA")))
        .andExpect(jsonPath("$.data.totalFiles", is(5)))
        .andExpect(jsonPath("$.data.processedFiles", is(5)))
        .andExpect(jsonPath("$.data.totalReviews", is(1250)))
        .andExpect(jsonPath("$.data.processedReviews", is(1200)))
        .andExpect(jsonPath("$.data.failedReviews", is(50)));

    verify(processingOrchestrationService).getProcessingStatus("processing-001");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getProcessingStatus_ShouldReturnNotFound_WhenProcessingDoesNotExist() throws Exception {
    // Given
    when(processingOrchestrationService.getProcessingStatus("non-existent"))
        .thenThrow(new RuntimeException("Processing not found"));

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/admin/processing/status/non-existent")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.error.code", is("PROCESSING_NOT_FOUND")));

    verify(processingOrchestrationService).getProcessingStatus("non-existent");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getAllProcessingStatuses_ShouldReturnAllStatuses_WhenRequested() throws Exception {
    // Given
    ProcessingStatusResponse anotherStatus =
        ProcessingStatusResponse.builder()
            .id("processing-002")
            .status(ProcessingStatus.IN_PROGRESS)
            .provider(ProviderType.BOOKING)
            .totalFiles(3)
            .processedFiles(1)
            .totalReviews(750)
            .processedReviews(250)
            .failedReviews(0)
            .startTime(LocalDateTime.of(2025, 4, 15, 11, 0, 0))
            .endTime(null)
            .errorMessage(null)
            .build();

    List<ProcessingStatusResponse> statuses = Arrays.asList(sampleProcessingStatus, anotherStatus);
    when(processingOrchestrationService.getAllProcessingStatuses()).thenReturn(statuses);

    // When & Then
    mockMvc
        .perform(get("/api/v1/admin/processing/status").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data", hasSize(2)))
        .andExpect(jsonPath("$.data[0].id", is("processing-001")))
        .andExpect(jsonPath("$.data[0].status", is("COMPLETED")))
        .andExpect(jsonPath("$.data[1].id", is("processing-002")))
        .andExpect(jsonPath("$.data[1].status", is("IN_PROGRESS")));

    verify(processingOrchestrationService).getAllProcessingStatuses();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void stopProcessing_ShouldStopProcessing_WhenProcessingIsRunning() throws Exception {
    // Given
    doNothing().when(processingOrchestrationService).stopProcessing("processing-002");

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/admin/processing/stop/processing-002")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.message", is("Processing stopped successfully")));

    verify(processingOrchestrationService).stopProcessing("processing-002");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void stopProcessing_ShouldReturnNotFound_WhenProcessingDoesNotExist() throws Exception {
    // Given
    doThrow(new RuntimeException("Processing not found"))
        .when(processingOrchestrationService)
        .stopProcessing("non-existent");

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/admin/processing/stop/non-existent")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.error.code", is("PROCESSING_NOT_FOUND")));

    verify(processingOrchestrationService).stopProcessing("non-existent");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getProcessingHistory_ShouldReturnHistory_WhenRequested() throws Exception {
    // Given
    List<ProcessingStatusResponse> history = Arrays.asList(sampleProcessingStatus);
    when(processingOrchestrationService.getProcessingHistory(eq(ProviderType.AGODA), any(), any()))
        .thenReturn(history);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/admin/processing/history")
                .param("provider", "AGODA")
                .param("limit", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is("processing-001")))
        .andExpect(jsonPath("$.data[0].provider", is("AGODA")));

    verify(processingOrchestrationService)
        .getProcessingHistory(eq(ProviderType.AGODA), any(), any());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getSystemHealth_ShouldReturnHealthStatus_WhenRequested() throws Exception {
    // Given
    Map<String, Object> healthStatus = new HashMap<>();
    healthStatus.put("status", "UP");
    healthStatus.put("database", "UP");
    healthStatus.put("s3", "UP");
    healthStatus.put("processing", "IDLE");
    healthStatus.put("lastProcessing", LocalDateTime.now().minusHours(2));
    healthStatus.put("totalReviewsProcessedToday", 5000);

    when(processingOrchestrationService.getSystemHealth()).thenReturn(healthStatus);

    // When & Then
    mockMvc
        .perform(get("/api/v1/admin/health").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.status", is("UP")))
        .andExpect(jsonPath("$.data.database", is("UP")))
        .andExpect(jsonPath("$.data.s3", is("UP")))
        .andExpect(jsonPath("$.data.processing", is("IDLE")))
        .andExpect(jsonPath("$.data.totalReviewsProcessedToday", is(5000)));

    verify(processingOrchestrationService).getSystemHealth();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getSystemMetrics_ShouldReturnMetrics_WhenRequested() throws Exception {
    // Given
    Map<String, Object> metrics = new HashMap<>();
    metrics.put("totalReviews", 50000L);
    metrics.put("reviewsProcessedLast24Hours", 1200L);
    metrics.put("averageProcessingTimeMinutes", 15.5);
    metrics.put("activeConnections", 10);
    metrics.put("memoryUsagePercent", 65.2);
    metrics.put("diskUsagePercent", 42.8);

    when(processingOrchestrationService.getSystemMetrics()).thenReturn(metrics);

    // When & Then
    mockMvc
        .perform(get("/api/v1/admin/metrics").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.totalReviews", is(50000)))
        .andExpect(jsonPath("$.data.reviewsProcessedLast24Hours", is(1200)))
        .andExpect(jsonPath("$.data.averageProcessingTimeMinutes", is(15.5)))
        .andExpect(jsonPath("$.data.activeConnections", is(10)))
        .andExpect(jsonPath("$.data.memoryUsagePercent", is(65.2)))
        .andExpect(jsonPath("$.data.diskUsagePercent", is(42.8)));

    verify(processingOrchestrationService).getSystemMetrics();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void clearProcessingHistory_ShouldClearHistory_WhenRequested() throws Exception {
    // Given
    doNothing().when(processingOrchestrationService).clearProcessingHistory(30);

    // When & Then
    mockMvc
        .perform(
            delete("/api/v1/admin/processing/history")
                .with(csrf())
                .param("olderThanDays", "30")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.message", is("Processing history cleared successfully")));

    verify(processingOrchestrationService).clearProcessingHistory(30);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void retryFailedProcessing_ShouldRetryProcessing_WhenValidRequest() throws Exception {
    // Given
    when(processingOrchestrationService.retryFailedProcessing("processing-001"))
        .thenReturn("processing-003");

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/admin/processing/retry/processing-001")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.processingId", is("processing-003")))
        .andExpect(jsonPath("$.message", is("Retry processing triggered successfully")));

    verify(processingOrchestrationService).retryFailedProcessing("processing-001");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void validateConfiguration_ShouldReturnConfigStatus_WhenRequested() throws Exception {
    // Given
    Map<String, Object> configStatus = new HashMap<>();
    configStatus.put("awsS3", true);
    configStatus.put("database", true);
    configStatus.put("scheduling", true);
    configStatus.put("errors", Arrays.asList());

    when(processingOrchestrationService.validateConfiguration()).thenReturn(configStatus);

    // When & Then
    mockMvc
        .perform(get("/api/v1/admin/config/validate").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.awsS3", is(true)))
        .andExpect(jsonPath("$.data.database", is(true)))
        .andExpect(jsonPath("$.data.scheduling", is(true)))
        .andExpect(jsonPath("$.data.errors", hasSize(0)));

    verify(processingOrchestrationService).validateConfiguration();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void triggerProcessing_ShouldReturnConflict_WhenProcessingAlreadyRunning() throws Exception {
    // Given
    when(processingOrchestrationService.triggerProcessing(any(ProcessingTriggerRequest.class)))
        .thenThrow(new RuntimeException("Processing already in progress"));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/admin/processing/trigger")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(triggerRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.error.code", is("PROCESSING_ALREADY_RUNNING")));

    verify(processingOrchestrationService).triggerProcessing(any(ProcessingTriggerRequest.class));
  }
}
