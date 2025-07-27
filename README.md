# Review System Microservice

A Spring Boot microservice for processing hotel reviews from S3 bucket.

## Requirements
- **Java:** 17.0.15
- **Maven:** 3.6+

## Pre-commit Hooks (Automated Quality Gates)

This project uses Maven to manage and install Git pre-commit hooks for code quality and reliability. No extra tools or manual copying required—just Maven!

### How it Works
- The `hooks/pre-commit` script is versioned in the repo.
- On `mvn install`, the script is automatically installed to `.git/hooks/pre-commit` and made executable.
- On every commit, the following checks run:
  - Code formatting (Spotless)
  - Linting (Checkstyle)
  - Static analysis (PMD)
  - Unit tests
  - Code coverage report (JaCoCo)
- If any check fails, the commit is blocked with a helpful message.

### Team Setup Instructions

1. **Clone the repository:**
   ```sh
   git clone <repo-url>
   cd <repo-directory>
   ```
2. **Install the Git hooks:**
   ```sh
   mvn install
   ```
   This will set up the pre-commit hook for you.
3. **Update hooks after changes:**
   If the `hooks/pre-commit` script changes, run `mvn install` again to update your local hook.
4. **Troubleshooting:**
   If you get a permissions error, run:
   ```sh
   chmod +x .git/hooks/pre-commit
   ```

## Project Structure
- `src/main/java/` - Application source code
- `src/test/java/` - Test code
- `hooks/pre-commit` - Versioned pre-commit hook script

## Project Details

# Epic 5: Scheduling & Orchestration

This epic implements the scheduled processing and orchestration layer for the Review System Microservice.

## Overview

The scheduling and orchestration system provides:
- **Scheduled Processing**: Automatic periodic processing of new review files
- **Distributed Locking**: Prevents concurrent execution across multiple instances
- **File Orchestration**: Manages file discovery, prioritization, and processing
- **Resource Management**: Thread pool management and graceful shutdown
- **Concurrent Processing**: Optional parallel processing of multiple files

## Components

### 1. ScheduledReviewProcessor
- **Location**: `src/main/java/com/reviewsystem/infrastructure/scheduler/ScheduledReviewProcessor.java`
- **Purpose**: Handles scheduled tasks with distributed locking
- **Features**:
    - Periodic review file processing (configurable interval)
    - Daily cleanup of old processed file records
    - Distributed locking to prevent concurrent execution
    - Comprehensive error handling and metrics recording

### 2. ProcessingOrchestrationService
- **Location**: `src/main/java/com/reviewsystem/application/service/ProcessingOrchestrationService.java`
- **Purpose**: Orchestrates the end-to-end file processing workflow
- **Features**:
    - File discovery from S3
    - File prioritization (chronological processing)
    - Sequential and concurrent processing modes
    - Resource management with thread pools
    - Graceful shutdown handling

### 3. SchedulingConfig
- **Location**: `src/main/java/com/reviewsystem/config/SchedulingConfig.java`
- **Purpose**: Configuration for scheduling and locking infrastructure
- **Features**:
    - Task scheduler configuration
    - Lock registry setup for distributed locking
    - Thread pool configuration

### 4. ProcessingMetrics (Stub)
- **Location**: `src/main/java/com/reviewsystem/infrastructure/monitoring/ProcessingMetrics.java`
- **Purpose**: Metrics collection for monitoring and observability
- **Features**:
    - Processing success/failure metrics
    - Performance timing metrics
    - Error tracking and reporting

## Configuration

### Scheduling Properties
```yaml
app:
  scheduling:
    review-processing:
      interval: 1800000  # 30 minutes (in milliseconds)
    cleanup:
      cron: "0 0 2 * * ?"  # Daily at 2 AM
    thread-pool:
      size: 5
      prefix: "review-scheduler-"
    lock:
      ttl: 3600000  # 1 hour (in milliseconds)
```

### Processing Properties
```yaml
app:
  processing:
    thread-pool:
      core-size: 5
      max-size: 10
      queue-capacity: 100
    concurrent:
      enabled: true
    cleanup:
      retention-days: 30
```

## Key Features

### Distributed Locking
- Prevents multiple instances from processing the same files simultaneously
- Uses Spring Integration's LockRegistry
- Configurable lock TTL to prevent deadlocks
- Separate locks for processing and cleanup operations

### File Prioritization
- Files are processed in chronological order
- Extracts date information from file paths: `reviews/YYYY/MM/DD/provider-reviews-YYYYMMDD.jl`
- Ensures consistent processing order across multiple runs

### Concurrent Processing
- Optional concurrent processing for improved performance
- Configurable thread pool with proper resource management
- Individual file failures don't stop processing of other files
- Comprehensive error handling and reporting

### Graceful Shutdown
- Proper cleanup of thread pools and resources
- Waits for current processing to complete before shutdown
- Configurable shutdown timeout

## Testing

### Unit Tests
- **ScheduledReviewProcessorTest**: Tests scheduling logic, locking, and error handling
- **ProcessingOrchestrationServiceTest**: Tests orchestration logic, prioritization, and concurrent processing
- **SchedulingConfigTest**: Tests configuration and lock registry setup

### Integration Tests
- **SchedulingIntegrationTest**: Tests distributed locking and concurrent execution prevention
- Verifies end-to-end scheduling behavior
- Tests health check functionality

### Running Tests
```bash
# Run all scheduling tests
mvn test -Dtest="**/scheduler/**" -Dspring.profiles.active=test-postgres

# Run specific test class
mvn test -Dtest=ScheduledReviewProcessorTest -Dspring.profiles.active=test-postgres

# Run integration tests
mvn test -Dtest=SchedulingIntegrationTest -Dspring.profiles.active=test-postgres
```

## Usage

### Automatic Processing
The system automatically processes new files based on the configured schedule:
```java
@Scheduled(fixedDelayString = "${app.scheduling.review-processing.interval:1800000}")
public void processReviews() {
    // Automatic processing every 30 minutes (default)
}
```

### Manual Processing
Processing can be triggered manually via the orchestration service:
```java
@Autowired
private ProcessingOrchestrationService orchestrationService;

public void triggerManualProcessing() {
    Long reviewsProcessed = orchestrationService.triggerManualProcessing();
}
```

### Health Monitoring
```java
@Autowired
private ScheduledReviewProcessor scheduler;

public boolean checkSchedulerHealth() {
    return scheduler.isSchedulerHealthy();
}
```

## Error Handling

### Processing Errors
- Individual file processing errors don't stop the entire batch
- Errors are logged and reported to metrics system
- Failed files can be retried in subsequent runs

### Lock Acquisition Failures
- When lock cannot be acquired, processing is skipped (not failed)
- Metrics are recorded for monitoring concurrent execution attempts
- Next scheduled run will attempt processing again

### Shutdown Handling
- Graceful shutdown with configurable timeout
- Current processing completes before shutdown
- Thread pools are properly cleaned up

## Monitoring

### Key Metrics
- Processing success/failure rates
- Number of files processed per run
- Processing duration and performance
- Lock acquisition success/failure
- Thread pool utilization

### Health Checks
- Scheduler health status
- Lock registry connectivity
- Thread pool status
- Processing statistics

## Production Considerations

### Distributed Lock Registry
For production deployment, replace the default lock registry with a distributed solution:
```java
@Bean
public LockRegistry lockRegistry() {
    // Use Redis-based lock registry for production
    return new RedisLockRegistry(redisConnectionFactory, "review-processing");
}
```

### Monitoring Integration
Integrate with monitoring systems (Prometheus, Micrometer, etc.):
```java
@Component
public class ProcessingMetrics {
    private final MeterRegistry meterRegistry;
    
    public void recordProcessingSuccess(Long count) {
        meterRegistry.counter("review.processing.success")
            .increment(count);
    }
}
```

### Scaling Considerations
- Configure appropriate thread pool sizes based on available resources
- Monitor memory usage during concurrent processing
- Consider implementing backpressure mechanisms for high-volume scenarios

## Dependencies

### Required Services (Stubs)
- `S3Service`: File discovery and download from S3
- `ReviewProcessingService`: Core file processing logic
- `FileTrackingService`: File processing state management

### Spring Dependencies
- `spring-boot-starter-integration`: For lock registry
- `spring-boot-starter-scheduling`: For @Scheduled support
- `spring-boot-starter-actuator`: For health checks and metrics

## Next Steps

1. **Epic 6**: Implement REST API endpoints for manual triggering and monitoring
2. **Epic 7**: Add comprehensive error handling and resilience patterns
3. **Epic 8**: Implement performance optimizations and advanced concurrent processing
4. **Production Setup**: Configure distributed locking and monitoring integrations