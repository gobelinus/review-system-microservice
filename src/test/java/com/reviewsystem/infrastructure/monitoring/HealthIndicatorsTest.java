package com.reviewsystem.infrastructure.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.reviewsystem.application.service.ProcessingOrchestrationService;
import com.reviewsystem.infrastructure.aws.S3Service;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test-postgres")
class HealthIndicatorsTest {

  @Mock private S3Service s3Service;

  @Mock private DataSource dataSource;

  @Mock private ProcessingOrchestrationService processingOrchestrationService;

  @Mock private Connection connection;

  private S3HealthIndicator s3HealthIndicator;
  private DatabaseHealthIndicator databaseHealthIndicator;
  private ProcessingHealthIndicator processingHealthIndicator;

  @BeforeEach
  void setUp() {
    s3HealthIndicator = new S3HealthIndicator(s3Service);
    databaseHealthIndicator = new DatabaseHealthIndicator(dataSource);
    processingHealthIndicator = new ProcessingHealthIndicator(processingOrchestrationService);
  }

  @Test
  void s3HealthIndicator_ShouldReturnUp_WhenS3IsHealthy() {
    // Given
    when(s3Service.checkConnectivity()).thenReturn(true);
    when(s3Service.getBucketName()).thenReturn("test-bucket");
    when(s3Service.getRegion()).thenReturn("us-east-1");

    // When
    Health health = s3HealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("bucket", "test-bucket");
    assertThat(health.getDetails()).containsEntry("region", "us-east-1");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(s3Service).checkConnectivity();
    verify(s3Service).getBucketName();
    verify(s3Service).getRegion();
  }

  @Test
  void s3HealthIndicator_ShouldReturnDown_WhenS3IsUnhealthy() {
    // Given
    when(s3Service.checkConnectivity()).thenReturn(false);
    when(s3Service.getBucketName()).thenReturn("test-bucket");

    // When
    Health health = s3HealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("bucket", "test-bucket");
    assertThat(health.getDetails()).containsEntry("error", "S3 connectivity check failed");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(s3Service).checkConnectivity();
    verify(s3Service).getBucketName();
  }

  @Test
  void s3HealthIndicator_ShouldReturnDown_WhenS3ThrowsException() {
    // Given
    when(s3Service.checkConnectivity()).thenThrow(new RuntimeException("Connection timeout"));
    when(s3Service.getBucketName()).thenReturn("test-bucket");

    // When
    Health health = s3HealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("bucket", "test-bucket");
    assertThat(health.getDetails()).containsEntry("error", "Connection timeout");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(s3Service).checkConnectivity();
    verify(s3Service).getBucketName();
  }

  @Test
  void databaseHealthIndicator_ShouldReturnUp_WhenDatabaseIsHealthy() throws SQLException {
    // Given
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(5)).thenReturn(true);
    when(connection.getMetaData()).thenReturn(mock(java.sql.DatabaseMetaData.class));
    when(connection.getMetaData().getURL()).thenReturn("jdbc:postgresql://localhost:5432/reviewdb");
    when(connection.getMetaData().getDatabaseProductName()).thenReturn("PostgreSQL");
    when(connection.getMetaData().getDatabaseProductVersion()).thenReturn("13.4");

    // When
    Health health = databaseHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
    assertThat(health.getDetails()).containsEntry("version", "13.4");
    assertThat(health.getDetails())
        .containsEntry("url", "jdbc:postgresql://localhost:5432/reviewdb");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(dataSource).getConnection();
    verify(connection).isValid(5);
    verify(connection).close();
  }

  @Test
  void databaseHealthIndicator_ShouldReturnDown_WhenDatabaseIsUnhealthy() throws SQLException {
    // Given
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(5)).thenReturn(false);

    // When
    Health health = databaseHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("error", "Database connection is not valid");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(dataSource).getConnection();
    verify(connection).isValid(5);
    verify(connection).close();
  }

  @Test
  void databaseHealthIndicator_ShouldReturnDown_WhenSQLExceptionThrown() throws SQLException {
    // Given
    when(dataSource.getConnection()).thenThrow(new SQLException("Connection pool exhausted"));

    // When
    Health health = databaseHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("error", "Connection pool exhausted");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(dataSource).getConnection();
  }

  @Test
  void processingHealthIndicator_ShouldReturnUp_WhenProcessingIsHealthy() {
    // Given
    Map<String, Object> processingStatus = new HashMap<>();
    processingStatus.put("status", "IDLE");
    processingStatus.put("activeProcesses", 0);
    processingStatus.put("lastProcessing", LocalDateTime.now().minusHours(1));
    processingStatus.put("totalProcessedToday", 1500);
    processingStatus.put("failedProcessesToday", 2);
    processingStatus.put("queueSize", 0);

    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(processingStatus);

    // When
    Health health = processingHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("status", "IDLE");
    assertThat(health.getDetails()).containsEntry("activeProcesses", 0);
    assertThat(health.getDetails()).containsEntry("totalProcessedToday", 1500);
    assertThat(health.getDetails()).containsEntry("failedProcessesToday", 2);
    assertThat(health.getDetails()).containsEntry("queueSize", 0);
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(processingOrchestrationService).getProcessingHealthStatus();
  }

  @Test
  void processingHealthIndicator_ShouldReturnDown_WhenTooManyFailures() {
    // Given
    Map<String, Object> processingStatus = new HashMap<>();
    processingStatus.put("status", "ERROR");
    processingStatus.put("activeProcesses", 0);
    processingStatus.put("lastProcessing", LocalDateTime.now().minusMinutes(30));
    processingStatus.put("totalProcessedToday", 100);
    processingStatus.put("failedProcessesToday", 50); // High failure rate
    processingStatus.put("queueSize", 25);

    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(processingStatus);

    // When
    Health health = processingHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("status", "ERROR");
    assertThat(health.getDetails()).containsEntry("failedProcessesToday", 50);
    assertThat(health.getDetails()).containsEntry("reason", "High failure rate detected");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(processingOrchestrationService).getProcessingHealthStatus();
  }

  @Test
  void processingHealthIndicator_ShouldReturnDown_WhenQueueIsFull() {
    // Given
    Map<String, Object> processingStatus = new HashMap<>();
    processingStatus.put("status", "RUNNING");
    processingStatus.put("activeProcesses", 3);
    processingStatus.put("lastProcessing", LocalDateTime.now().minusMinutes(5));
    processingStatus.put("totalProcessedToday", 500);
    processingStatus.put("failedProcessesToday", 5);
    processingStatus.put("queueSize", 1000); // Queue is full

    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(processingStatus);

    // When
    Health health = processingHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("queueSize", 1000);
    assertThat(health.getDetails()).containsEntry("reason", "Processing queue is full");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(processingOrchestrationService).getProcessingHealthStatus();
  }

  @Test
  void processingHealthIndicator_ShouldReturnDown_WhenServiceThrowsException() {
    // Given
    when(processingOrchestrationService.getProcessingHealthStatus())
        .thenThrow(new RuntimeException("Service unavailable"));

    // When
    Health health = processingHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("error", "Service unavailable");
    assertThat(health.getDetails()).containsKey("lastChecked");

    verify(processingOrchestrationService).getProcessingHealthStatus();
  }

  @Test
  void processingHealthIndicator_ShouldReturnUp_WhenProcessingIsRunning() {
    // Given
    Map<String, Object> processingStatus = new HashMap<>();
    processingStatus.put("status", "RUNNING");
    processingStatus.put("activeProcesses", 2);
    processingStatus.put("lastProcessing", LocalDateTime.now().minusMinutes(2));
    processingStatus.put("totalProcessedToday", 800);
    processingStatus.put("failedProcessesToday", 5);
    processingStatus.put("queueSize", 15);

    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(processingStatus);

    // When
    Health health = processingHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("status", "RUNNING");
    assertThat(health.getDetails()).containsEntry("activeProcesses", 2);
    assertThat(health.getDetails()).containsEntry("queueSize", 15);

    verify(processingOrchestrationService).getProcessingHealthStatus();
  }

  @Test
  void processingHealthIndicator_ShouldReturnOutOfService_WhenProcessingIsPaused() {
    // Given
    Map<String, Object> processingStatus = new HashMap<>();
    processingStatus.put("status", "PAUSED");
    processingStatus.put("activeProcesses", 0);
    processingStatus.put("lastProcessing", LocalDateTime.now().minusHours(2));
    processingStatus.put("totalProcessedToday", 300);
    processingStatus.put("failedProcessesToday", 0);
    processingStatus.put("queueSize", 0);

    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(processingStatus);

    // When
    Health health = processingHealthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    assertThat(health.getDetails()).containsEntry("status", "PAUSED");
    assertThat(health.getDetails())
        .containsEntry("reason", "Processing is administratively paused");

    verify(processingOrchestrationService).getProcessingHealthStatus();
  }

  @Test
  void processingHealthIndicator_ShouldIncludeTimestampInDetails() {
    // Given
    Map<String, Object> processingStatus = new HashMap<>();
    processingStatus.put("status", "IDLE");
    processingStatus.put("activeProcesses", 0);
    processingStatus.put("queueSize", 0);

    when(processingOrchestrationService.getProcessingHealthStatus()).thenReturn(processingStatus);

    // When
    Health health = processingHealthIndicator.health();

    // Then
    assertThat(health.getDetails()).containsKey("lastChecked");
    assertThat(health.getDetails().get("lastChecked")).isInstanceOf(LocalDateTime.class);

    LocalDateTime lastChecked = (LocalDateTime) health.getDetails().get("lastChecked");
    assertThat(lastChecked).isAfter(LocalDateTime.now().minusMinutes(1));
    assertThat(lastChecked).isBefore(LocalDateTime.now().plusMinutes(1));
  }
}
