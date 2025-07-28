package com.reviewsystem.domain.repository;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.ProcessingJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository interface for ProcessingJob entity */
@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, String> {

  /** Find all processing jobs by status */
  List<ProcessingJob> findByStatusOrderByCreatedAtDesc(ProcessingStatus status);

  /** Find processing jobs by provider and date range */
  @Query(
      "SELECT pj FROM ProcessingJob pj WHERE "
          + "(:provider IS NULL OR pj.provider = :provider) AND "
          + "pj.createdAt >= :startDate AND pj.createdAt <= :endDate "
          + "ORDER BY pj.createdAt DESC")
  List<ProcessingJob> findByProviderAndDateRange(
      @Param("provider") ProviderType provider,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  /** Find processing jobs by provider and date range with limit */
  @Query(
      "SELECT pj FROM ProcessingJob pj WHERE "
          + "(:provider IS NULL OR pj.provider = :provider) AND "
          + "pj.createdAt >= :startDate AND pj.createdAt <= :endDate "
          + "ORDER BY pj.createdAt DESC")
  Page<ProcessingJob> findByProviderAndDateRange(
      @Param("provider") ProviderType provider,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      Pageable pageable);

  /** Find all active (in-progress) processing jobs */
  List<ProcessingJob> findByStatusIn(List<ProcessingStatus> statuses);

  /** Find the most recent processing job */
  Optional<ProcessingJob> findTopByOrderByCreatedAtDesc();

  /** Count processing jobs by status in date range */
  @Query(
      "SELECT COUNT(pj) FROM ProcessingJob pj WHERE "
          + "pj.status = :status AND "
          + "pj.createdAt >= :startDate AND pj.createdAt <= :endDate")
  long countByStatusAndDateRange(
      @Param("status") ProcessingStatus status,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  /** Find processing jobs older than specified days */
  @Query("SELECT pj FROM ProcessingJob pj WHERE pj.createdAt < :cutoffDate")
  List<ProcessingJob> findOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

  /** Delete processing jobs older than specified date */
  @Modifying
  @Query("DELETE FROM ProcessingJob pj WHERE pj.createdAt < :cutoffDate")
  int deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

  /** Find failed processing jobs that can be retried */
  @Query("SELECT pj FROM ProcessingJob pj WHERE pj.status = :status AND pj.endTime IS NOT NULL")
  List<ProcessingJob> findRetryableJobs(@Param("status") ProcessingStatus status);

  /** Get processing statistics for dashboard */
  @Query(
      "SELECT "
          + "COUNT(CASE WHEN pj.status = 'COMPLETED' THEN 1 END) as completed, "
          + "COUNT(CASE WHEN pj.status = 'FAILED' THEN 1 END) as failed, "
          + "COUNT(CASE WHEN pj.status = 'IN_PROGRESS' THEN 1 END) as inProgress, "
          + "COUNT(CASE WHEN pj.status = 'CANCELLED' THEN 1 END) as cancelled, "
          + "COALESCE(SUM(pj.totalReviews), 0) as totalReviews "
          + "FROM ProcessingJob pj WHERE pj.createdAt >= :since")
  Object[] getProcessingStatistics(@Param("since") LocalDateTime since);

  /** Find processing jobs by multiple providers */
  List<ProcessingJob> findByProviderInOrderByCreatedAtDesc(List<ProviderType> providers);
}
