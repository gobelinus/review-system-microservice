package com.reviewsystem.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

  @Nested
  @DisplayName("Scheduling Configuration Tests")
  class SchedulingConfigurationTest {
    @Test
    void taskScheduler_ShouldBeConfiguredCorrectly() {
      // Then
      assertNotNull(taskScheduler, "ThreadPoolTaskScheduler should be autowired");

      assertTrue(
          taskScheduler.getThreadNamePrefix().startsWith("review-scheduler-"),
          "Thread name prefix should start with 'review-scheduler-'");
    }

    @Test
    void lockRegistry_ShouldBeConfiguredCorrectly() {
      // Then
      assertNotNull(lockRegistry);
    }
  }

  @Nested
  @DisplayName("Scheduling Locking Tests")
  class SchedulingLockTest {
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
    void lockRegistry_ShouldAllowConcurrentAccessInSameThread() {
      // Given
      String lockKey = "concurrent-test-lock";
      Lock lock1 = lockRegistry.obtain(lockKey);
      Lock lock2 = lockRegistry.obtain(lockKey);

      // When
      boolean acquired1 = lock1.tryLock();
      boolean acquired2 = lock2.tryLock();

      // Then
      assertTrue(acquired1);
      assertTrue(acquired2); // Second acquisition should fail

      // Cleanup
      try {
        if (acquired1) {
          lock1.unlock();
        }
        if (acquired2) {
          lock2.unlock();
        }
      } catch (Exception e) {
        // do nothing
      }
    }

    @Test
    void lockRegistry_ShouldPreventConcurrentAccess() throws InterruptedException {
      // Given
      String lockKey = "concurrent-test-lock";
      Lock lock1 = lockRegistry.obtain(lockKey);

      AtomicBoolean acquiredInOtherThread = new AtomicBoolean(false);
      CountDownLatch latch = new CountDownLatch(1);
      ExecutorService executorService = Executors.newSingleThreadExecutor();

      // When
      boolean acquired1 = lock1.tryLock();
      assertTrue(acquired1, "First lock acquisition should succeed");

      if (acquired1) {
        // Attempt to acquire the lock from a different thread
        executorService.submit(
            () -> {
              Lock lock2 = lockRegistry.obtain(lockKey);
              boolean currentAcquired2 = lock2.tryLock();
              acquiredInOtherThread.set(currentAcquired2);
              latch.countDown(); // Signal that the other thread has attempted to acquire the lock
            });

        // Wait for the other thread to attempt to acquire the lock
        latch.await(5, TimeUnit.SECONDS); // Wait with a timeout

        // Then
        assertFalse(
            acquiredInOtherThread.get(),
            "Second lock acquisition from different thread should fail");

        // Cleanup
        lock1.unlock();
      }

      executorService.shutdown();
      assertTrue(
          executorService.awaitTermination(5, TimeUnit.SECONDS),
          "Executor service should terminate");
    }
  }
}
