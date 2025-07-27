package com.reviewsystem.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {SchedulingConfig.class})
@ActiveProfiles("test-postgres")
class SchedulingConfigTest {

  @Autowired private ThreadPoolTaskScheduler taskScheduler;

  @Autowired private LockRegistry lockRegistry;

  @Test
  void taskScheduler_ShouldBeConfiguredCorrectly() {
    // Then
    assertNotNull(taskScheduler);
    assertEquals(5, taskScheduler.getPoolSize()); // Default value
    assertTrue(taskScheduler.getThreadNamePrefix().startsWith("review-scheduler-"));
  }

  @Test
  void lockRegistry_ShouldBeConfiguredCorrectly() {
    // Then
    assertNotNull(lockRegistry);
  }

  @Test
  void lockRegistry_ShouldProvideWorkingLocks() {
    // Given
    String lockKey = "test-lock";

    // When
    Lock lock1 = lockRegistry.obtain(lockKey);
    Lock lock2 = lockRegistry.obtain(lockKey);

    // Then
    assertNotNull(lock1);
    assertNotNull(lock2);
    assertSame(lock1, lock2); // Should return the same lock instance for the same key
  }

  @Test
  void lockRegistry_ShouldSupportLockAcquisition() {
    // Given
    String lockKey = "acquisition-test-lock";
    Lock lock = lockRegistry.obtain(lockKey);

    // When
    boolean acquired = lock.tryLock();

    // Then
    assertTrue(acquired);

    // Cleanup
    if (acquired) {
      lock.unlock();
    }
  }

  @Test
  void lockRegistry_ShouldPreventConcurrentAccess() {
    // Given
    String lockKey = "concurrent-test-lock";
    Lock lock1 = lockRegistry.obtain(lockKey);
    Lock lock2 = lockRegistry.obtain(lockKey);

    // When
    boolean acquired1 = lock1.tryLock();
    boolean acquired2 = lock2.tryLock();

    // Then
    assertTrue(acquired1);
    assertFalse(acquired2); // Second acquisition should fail

    // Cleanup
    if (acquired1) {
      lock1.unlock();
    }
  }
}
