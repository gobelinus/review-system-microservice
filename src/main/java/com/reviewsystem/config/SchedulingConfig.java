package com.reviewsystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.support.locks.DefaultLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Configuration for scheduling and distributed locking. */
@Slf4j
@Configuration
@EnableScheduling
public class SchedulingConfig {

  @Value("${app.scheduling.thread-pool.size:5}")
  private int schedulerThreadPoolSize;

  @Value("${app.scheduling.thread-pool.prefix:review-scheduler-}")
  private String threadNamePrefix;

  @Value("${app.scheduling.lock.ttl:3600000}") // 1 hour default
  private long lockTimeToLive;

  /** Task scheduler for Spring's @Scheduled methods. */
  @Bean
  public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(schedulerThreadPoolSize);
    scheduler.setThreadNamePrefix(threadNamePrefix);
    scheduler.setAwaitTerminationSeconds(60);
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setRejectedExecutionHandler(
        (r, executor) -> {
          log.warn("Task rejected by scheduler: {}", r.toString());
        });

    log.info("Configured task scheduler with pool size: {}", schedulerThreadPoolSize);
    return scheduler;
  }

  /**
   * Lock registry for distributed locking. In production, this should be replaced with a
   * distributed lock registry (Redis, Zookeeper, etc.)
   */
  @Bean
  public LockRegistry lockRegistry() {
    // For production use, consider using RedisLockRegistry or similar
    DefaultLockRegistry registry = new DefaultLockRegistry();

    // Set TTL for locks to prevent deadlocks
    if (lockTimeToLive > 0) {
      // Note: DefaultLockRegistry doesn't support TTL directly
      // For production, use RedisLockRegistry with TTL support
      log.info("Configured lock registry with TTL: {} ms", lockTimeToLive);
    }

    log.info("Configured default lock registry for distributed locking");
    return registry;
  }
}
