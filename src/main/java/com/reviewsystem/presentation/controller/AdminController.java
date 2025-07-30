package com.reviewsystem.presentation.controller;

import com.reviewsystem.application.dto.request.ProcessingTriggerRequest;
import com.reviewsystem.application.dto.response.ApiResponse;
import com.reviewsystem.application.dto.response.ProcessingStatusResponse;
import com.reviewsystem.application.service.ProcessingOrchestrationService;
import com.reviewsystem.common.enums.ProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Admin", description = "Administrative endpoints for system management")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

  private final ProcessingOrchestrationService processingOrchestrationService;

  @PostMapping("/processing/trigger")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Trigger manual processing of review files")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "Processing triggered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Processing already in progress"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<ApiResponse<Map<String, String>>> triggerProcessing(
      @Valid @RequestBody ProcessingTriggerRequest request) {

    log.info(
        "Admin triggering processing - provider: {}, forceReprocess: {}, maxFiles: {}",
        request.getProvider(),
        request.isForceReprocess(),
        request.getMaxFiles());

    try {
      String processingId = processingOrchestrationService.triggerProcessing(request);

      Map<String, String> response = new HashMap<>();
      response.put("processingId", processingId);

      log.info("Processing triggered successfully with ID: {}", processingId);

      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(ApiResponse.success(response, "Processing triggered successfully"));

    } catch (IllegalStateException e) {
      log.warn("Processing trigger failed - already running: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ApiResponse.error("PROCESSING_ALREADY_RUNNING", e.getMessage()));
    }
  }

  @GetMapping("/processing/status/{processingId}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get processing status by ID")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved processing status"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Processing not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<ProcessingStatusResponse>> getProcessingStatus(
      @Parameter(description = "Processing ID", required = true) @PathVariable
          String processingId) {

    log.info("Fetching processing status for ID: {}", processingId);

    try {
      ProcessingStatusResponse status =
          processingOrchestrationService.getProcessingStatus(processingId);

      log.info("Successfully retrieved processing status for ID: {}", processingId);

      return ResponseEntity.ok(ApiResponse.success(status));

    } catch (RuntimeException e) {
      log.warn("Processing status not found for ID: {}", processingId);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.error(
                  "PROCESSING_NOT_FOUND", "Processing not found with ID: " + processingId));
    }
  }

  @GetMapping("/processing/status")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get all processing statuses")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved all processing statuses"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<List<ProcessingStatusResponse>>> getAllProcessingStatuses() {

    log.info("Fetching all processing statuses");

    List<ProcessingStatusResponse> statuses =
        processingOrchestrationService.getAllProcessingStatuses();

    log.info("Retrieved {} processing statuses", statuses.size());

    return ResponseEntity.ok(ApiResponse.success(statuses));
  }

  @PostMapping("/processing/stop/{processingId}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Stop running processing")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Processing stopped successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Processing not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Processing cannot be stopped"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Void>> stopProcessing(
      @Parameter(description = "Processing ID", required = true) @PathVariable
          String processingId) {

    log.info("Admin stopping processing with ID: {}", processingId);

    try {
      processingOrchestrationService.stopProcessing(processingId);

      log.info("Processing stopped successfully for ID: {}", processingId);

      return ResponseEntity.ok(ApiResponse.success(null, "Processing stopped successfully"));

    } catch (RuntimeException e) {
      log.warn("Failed to stop processing for ID: {} - {}", processingId, e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.error(
                  "PROCESSING_NOT_FOUND", "Processing not found with ID: " + processingId));
    }
  }

  @GetMapping("/processing/history")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get processing history with optional filters")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved processing history"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<List<ProcessingStatusResponse>>> getProcessingHistory(
      @Parameter(description = "Filter by provider") @RequestParam(required = false)
          ProviderType provider,
      @Parameter(description = "Start date for history range")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @Parameter(description = "End date for history range")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @Parameter(description = "Maximum number of records to return")
          @RequestParam(defaultValue = "50")
          @Min(1)
          Integer limit) {

    log.info(
        "Fetching processing history - provider: {}, startDate: {}, endDate: {}, limit: {}",
        provider,
        startDate,
        endDate,
        limit);

    List<ProcessingStatusResponse> history =
        processingOrchestrationService.getProcessingHistory(provider, startDate, endDate, limit);

    log.info("Retrieved {} processing history records", history.size());

    return ResponseEntity.ok(ApiResponse.success(history));
  }

  @PostMapping("/processing/retry/{processingId}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Retry failed processing")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "Retry processing triggered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Original processing not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Processing cannot be retried"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Map<String, String>>> retryFailedProcessing(
      @Parameter(description = "Processing ID to retry", required = true) @PathVariable
          String processingId) {

    log.info("Admin retrying failed processing with ID: {}", processingId);

    try {
      String newProcessingId = processingOrchestrationService.retryFailedProcessing(processingId);

      Map<String, String> response = new HashMap<>();
      response.put("processingId", newProcessingId);

      log.info("Retry processing triggered successfully with new ID: {}", newProcessingId);

      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(ApiResponse.success(response, "Retry processing triggered successfully"));

    } catch (RuntimeException e) {
      log.warn("Failed to retry processing for ID: {} - {}", processingId, e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.error(
                  "PROCESSING_NOT_FOUND", "Processing not found with ID: " + processingId));
    }
  }

  @DeleteMapping("/processing/history")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Clear old processing history")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Processing history cleared successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Void>> clearProcessingHistory(
      @Parameter(description = "Clear history older than specified days")
          @RequestParam(defaultValue = "30")
          @Min(1)
          Integer olderThanDays) {

    log.info("Admin clearing processing history older than {} days", olderThanDays);

    processingOrchestrationService.clearProcessingHistory(olderThanDays);

    log.info("Processing history cleared successfully");

    return ResponseEntity.ok(ApiResponse.success(null, "Processing history cleared successfully"));
  }

  @GetMapping("/health")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get comprehensive system health status")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved system health"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth() {

    log.info("Admin fetching system health status");

    Map<String, Object> healthStatus = processingOrchestrationService.getSystemHealth();

    log.info("System health status retrieved successfully");

    return ResponseEntity.ok(ApiResponse.success(healthStatus));
  }

  @GetMapping("/metrics")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get system metrics and statistics")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved system metrics"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemMetrics() {

    log.info("Admin fetching system metrics");

    Map<String, Object> metrics = processingOrchestrationService.getSystemMetrics();

    log.info("System metrics retrieved successfully");

    return ResponseEntity.ok(ApiResponse.success(metrics));
  }

  @GetMapping("/config/validate")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Validate system configuration")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Configuration validation completed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Map<String, Object>>> validateConfiguration() {

    log.info("Admin validating system configuration");

    Map<String, Object> configStatus = processingOrchestrationService.validateConfiguration();

    log.info("Configuration validation completed");

    return ResponseEntity.ok(ApiResponse.success(configStatus));
  }

  @PostMapping("/processing/pause")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Pause all scheduled processing")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Scheduled processing paused successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Void>> pauseScheduledProcessing() {

    log.info("Admin pausing scheduled processing");

    processingOrchestrationService.pauseScheduledProcessing();

    log.info("Scheduled processing paused successfully");

    return ResponseEntity.ok(ApiResponse.success(null, "Scheduled processing paused successfully"));
  }

  @PostMapping("/processing/resume")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Resume all scheduled processing")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Scheduled processing resumed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Void>> resumeScheduledProcessing() {

    log.info("Admin resuming scheduled processing");

    processingOrchestrationService.resumeScheduledProcessing();

    log.info("Scheduled processing resumed successfully");

    return ResponseEntity.ok(
        ApiResponse.success(null, "Scheduled processing resumed successfully"));
  }

  @GetMapping("/processing/logs/{processingId}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get processing logs for a specific processing run")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved processing logs"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Processing not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<List<String>>> getProcessingLogs(
      @Parameter(description = "Processing ID", required = true) @PathVariable String processingId,
      @Parameter(description = "Maximum number of log lines to return")
          @RequestParam(defaultValue = "100")
          @Min(1)
          Integer limit) {

    log.info("Admin fetching processing logs for ID: {}, limit: {}", processingId, limit);

    try {
      List<String> logs = processingOrchestrationService.getProcessingLogs(processingId, limit);

      log.info("Retrieved {} log lines for processing ID: {}", logs.size(), processingId);

      return ResponseEntity.ok(ApiResponse.success(logs));

    } catch (RuntimeException e) {
      log.warn("Processing logs not found for ID: {}", processingId);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.error(
                  "PROCESSING_NOT_FOUND", "Processing not found with ID: " + processingId));
    }
  }

  /* ToDo: Enable this after enabling cache
  @PostMapping("/cache/clear")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Clear application caches")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Caches cleared successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Void>> clearCaches(
      @Parameter(description = "Specific cache name to clear (optional)")
          @RequestParam(required = false)
          String cacheName) {

    log.info("Admin clearing caches - cacheName: {}", cacheName);

    processingOrchestrationService.clearCaches(cacheName);

    log.info("Caches cleared successfully");

    return ResponseEntity.ok(ApiResponse.success(null, "Caches cleared successfully"));
  }
  */

  @GetMapping("/providers/status")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get status for all providers")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved provider statuses"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required")
      })
  public ResponseEntity<ApiResponse<Map<ProviderType, Map<String, Object>>>> getProviderStatuses() {

    log.info("Admin fetching provider statuses");

    Map<ProviderType, Map<String, Object>> providerStatuses =
        processingOrchestrationService.getProviderStatuses();

    log.info("Provider statuses retrieved successfully");

    return ResponseEntity.ok(ApiResponse.success(providerStatuses));
  }
}
