// ============================================================================
// MONITORING CONFIGURATION
// ============================================================================

package com.reviewsystem.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import java.time.Duration;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitoringConfig {

  @Bean
  public MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer() {
    return registry -> {
      registry
          .config()
          .commonTags("application", "review-system-microservice")
          .meterFilter(
              MeterFilter.deny(
                  id -> {
                    String uri = id.getTag("uri");
                    return uri != null && uri.startsWith("/actuator");
                  }))
          .meterFilter(MeterFilter.maxExpected("api.request.duration", Duration.ofSeconds(10)))
          .meterFilter(MeterFilter.maxExpected("processing.duration", Duration.ofHours(2)));
    };
  }

  @Bean
  public DistributionStatisticConfig distributionStatisticConfig() {
    return DistributionStatisticConfig.builder()
        .percentilesHistogram(true)
        .percentiles(0.5, 0.95, 0.99)
        .sla(
            Duration.ofMillis(100).toMillis(),
            Duration.ofMillis(500).toMillis(),
            Duration.ofSeconds(1).toMillis())
        .minimumExpectedValue(0.01)
        .maximumExpectedValue(Duration.ofSeconds(30).getSeconds())
        .build();
  }
}
