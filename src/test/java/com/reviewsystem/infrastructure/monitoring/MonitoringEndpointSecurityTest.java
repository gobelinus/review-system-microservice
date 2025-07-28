package com.reviewsystem.infrastructure.monitoring;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reviewsystem.application.service.ProcessingOrchestrationService;
import com.reviewsystem.infrastructure.aws.S3Service;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test-postgres")
class MonitoringEndpointSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private S3Service s3Service;

  @MockBean private DataSource dataSource;

  @MockBean private ProcessingOrchestrationService processingOrchestrationService;

  @Test
  @WithAnonymousUser
  void healthEndpoint_ShouldBeAccessible_WhenAnonymous() throws Exception {
    // Given
    when(s3Service.checkConnectivity()).thenReturn(true);
    Map<String, Object> healthStatus = new HashMap<>();
    healthStatus.put("status", "UP");
    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(healthStatus);

    // When & Then
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").exists());
  }

  @Test
  @WithAnonymousUser
  void detailedHealthEndpoint_ShouldRequireAuthentication_WhenAnonymous() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/health/detailed")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "USER")
  void detailedHealthEndpoint_ShouldBeForbidden_WhenUserRole() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/health/detailed")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void detailedHealthEndpoint_ShouldBeAccessible_WhenAdminRole() throws Exception {
    // Given
    when(s3Service.checkConnectivity()).thenReturn(true);
    Map<String, Object> healthStatus = new HashMap<>();
    healthStatus.put("status", "UP");
    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(healthStatus);

    // When & Then
    mockMvc
        .perform(get("/actuator/health/detailed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").exists())
        .andExpect(jsonPath("$.components").exists());
  }

  @Test
  @WithAnonymousUser
  void metricsEndpoint_ShouldRequireAuthentication_WhenAnonymous() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "USER")
  void metricsEndpoint_ShouldBeForbidden_WhenUserRole() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void metricsEndpoint_ShouldBeAccessible_WhenAdminRole() throws Exception {
    // When & Then
    mockMvc
        .perform(get("/actuator/metrics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.names").exists());
  }

  @Test
  @WithAnonymousUser
  void infoEndpoint_ShouldBeAccessible_WhenAnonymous() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
  }

  @Test
  @WithAnonymousUser
  void prometheusEndpoint_ShouldRequireAuthentication_WhenAnonymous() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "MONITOR")
  void prometheusEndpoint_ShouldBeAccessible_WhenMonitorRole() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void prometheusEndpoint_ShouldBeAccessible_WhenAdminRole() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
  }

  @Test
  @WithAnonymousUser
  void customHealthIndicators_ShouldBeSecured_WhenAnonymous() throws Exception {
    // When & Then - S3 health indicator
    mockMvc.perform(get("/actuator/health/s3")).andExpect(status().isUnauthorized());

    // When & Then - Database health indicator
    mockMvc.perform(get("/actuator/health/database")).andExpect(status().isUnauthorized());

    // When & Then - Processing health indicator
    mockMvc.perform(get("/actuator/health/processing")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void customHealthIndicators_ShouldBeAccessible_WhenAdminRole() throws Exception {
    // Given
    when(s3Service.checkConnectivity()).thenReturn(true);
    when(s3Service.getBucketName()).thenReturn("test-bucket");
    when(s3Service.getRegion()).thenReturn("us-east-1");

    Map<String, Object> healthStatus = new HashMap<>();
    healthStatus.put("status", "UP");
    healthStatus.put("activeProcesses", 0);
    healthStatus.put("queueSize", 0);
    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(healthStatus);

    // When & Then - S3 health indicator
    mockMvc
        .perform(get("/actuator/health/s3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.details.bucket").value("test-bucket"));

    // When & Then - Processing health indicator
    mockMvc
        .perform(get("/actuator/health/processing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.details.status").value("UP"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void specificMetrics_ShouldBeAccessible_WhenAdminRole() throws Exception {
    // When & Then - JVM metrics
    mockMvc
        .perform(get("/actuator/metrics/jvm.memory.used"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("jvm.memory.used"));

    // When & Then - Application metrics (would exist after processing)
    mockMvc.perform(get("/actuator/metrics/reviews.processed.total")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "USER")
  void metricsCollection_ShouldNotExposeCustomMetrics_WhenUserRole() throws Exception {
    // When & Then - Custom application metrics should be forbidden
    mockMvc
        .perform(get("/actuator/metrics/reviews.processed.total"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/actuator/metrics/files.processed.total"))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/actuator/metrics/processing.duration")).andExpect(status().isForbidden());
  }

  @Test
  @WithAnonymousUser
  void managementEndpoints_ShouldRequireAuthentication_WhenAnonymous() throws Exception {
    // When & Then - Shutdown endpoint
    mockMvc.perform(get("/actuator/shutdown")).andExpect(status().isUnauthorized());

    // When & Then - Environment endpoint
    mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());

    // When & Then - Configuration properties endpoint
    mockMvc.perform(get("/actuator/configprops")).andExpect(status().isUnauthorized());

    // When & Then - Beans endpoint
    mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void managementEndpoints_ShouldBeAccessible_WhenAdminRole() throws Exception {
    // When & Then - Environment endpoint
    mockMvc
        .perform(get("/actuator/env"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeProfiles").exists());

    // When & Then - Configuration properties endpoint
    mockMvc.perform(get("/actuator/configprops")).andExpect(status().isOk());

    // When & Then - Beans endpoint
    mockMvc
        .perform(get("/actuator/beans"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contexts").exists());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void loggersEndpoint_ShouldBeAccessible_WhenAdminRole() throws Exception {
    // When & Then - View all loggers
    mockMvc
        .perform(get("/actuator/loggers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loggers").exists());

    // When & Then - View specific logger
    mockMvc
        .perform(get("/actuator/loggers/com.reviewsystem"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configuredLevel").exists());
  }

  @Test
  @WithMockUser(roles = "USER")
  void loggersEndpoint_ShouldBeForbidden_WhenUserRole() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/loggers")).andExpect(status().isForbidden());

    mockMvc.perform(get("/actuator/loggers/com.reviewsystem")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void heapdumpEndpoint_ShouldBeSecured_WhenAdminRole() throws Exception {
    // When & Then - Heapdump should be available but properly secured
    mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isOk());
  }

  @Test
  @WithAnonymousUser
  void heapdumpEndpoint_ShouldRequireAuthentication_WhenAnonymous() throws Exception {
    // When & Then
    mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "USER")
  void sensitiveEndpoints_ShouldBeForbidden_WhenUserRole() throws Exception {
    // When & Then - Environment variables (potentially sensitive)
    mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());

    // When & Then - Thread dump
    mockMvc.perform(get("/actuator/threaddump")).andExpect(status().isForbidden());

    // When & Then - Heap dump
    mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isForbidden());

    // When & Then - Configuration properties
    mockMvc.perform(get("/actuator/configprops")).andExpect(status().isForbidden());
  }

  @Test
  @WithAnonymousUser
  void actuatorBaseEndpoint_ShouldShowOnlyPublicEndpoints_WhenAnonymous() throws Exception {
    // When & Then
    mockMvc
        .perform(get("/actuator"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$._links.health").exists())
        .andExpect(jsonPath("$._links.info").exists())
        // Should not expose sensitive endpoints
        .andExpect(jsonPath("$._links.env").doesNotExist())
        .andExpect(jsonPath("$._links.metrics").doesNotExist())
        .andExpect(jsonPath("$._links.heapdump").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void actuatorBaseEndpoint_ShouldShowAllEndpoints_WhenAdminRole() throws Exception {
    // When & Then
    mockMvc
        .perform(get("/actuator"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$._links.health").exists())
        .andExpect(jsonPath("$._links.info").exists())
        .andExpect(jsonPath("$._links.metrics").exists())
        .andExpect(jsonPath("$._links.env").exists())
        .andExpect(jsonPath("$._links.configprops").exists())
        .andExpect(jsonPath("$._links.loggers").exists());
  }

  @Test
  @WithMockUser(roles = "MONITOR")
  void monitorRole_ShouldHaveLimitedAccess() throws Exception {
    // When & Then - Should have access to metrics and health
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());

    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());

    // But not sensitive endpoints
    mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());

    mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isForbidden());
  }
}
