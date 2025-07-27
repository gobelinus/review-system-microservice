package com.reviewsystem.infrastructure.persistence.jpa;

import com.reviewsystem.common.enums.ProcessingStatus;
import com.reviewsystem.domain.entity.ProcessedFile;
import com.reviewsystem.domain.repository.ProcessedFileRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA Repository implementation for ProcessedFile entity */
@Repository
public interface ProcessedFileJpaRepository
    extends JpaRepository<ProcessedFile, Long>, ProcessedFileRepository {

  @Override
  Optional<ProcessedFile> findByS3KeyAndEtag(String s3Key, String etag);

  @Override
  Optional<ProcessedFile> findByS3Key(String s3Key);

  @Override
  boolean existsByS3KeyAndEtag(String s3Key, String etag);

  @Override
  List<ProcessedFile> findByProcessingStatus(ProcessingStatus status);

  @Override
  List<ProcessedFile> findByCreatedAtAfter(LocalDateTime dateTime);

  @Override
  @Query(
      "SELECT pf FROM ProcessedFile pf WHERE pf.createdAt < :cutoffDate AND pf.processingStatus IN :statuses ORDER BY pf.createdAt ASC")
  List<ProcessedFile> findOldProcessedFiles(
      @Param("cutoffDate") LocalDateTime cutoffDate,
      @Param("statuses") List<ProcessingStatus> statuses);

  @Override
  @Query(
      "SELECT pf FROM ProcessedFile pf WHERE pf.processingStatus = 'PROCESSING' AND pf.processingStartedAt < :cutoffDateTime")
  List<ProcessedFile> findStuckProcessingFiles(
      @Param("cutoffDateTime") LocalDateTime cutoffDateTime);

  @Override
  @Query(
      "SELECT pf FROM ProcessedFile pf WHERE pf.provider = :provider AND pf.createdAt >= :since AND pf.processingStatus = :status ORDER BY pf.createdAt DESC")
  List<ProcessedFile> findRecentlyProcessedFiles(
      @Param("provider") String provider,
      @Param("since") LocalDateTime since,
      @Param("status") ProcessingStatus status);

  @Override
  long countByProcessingStatus(ProcessingStatus status);

  @Override
  @Modifying
  @Transactional
  @Query(
      "DELETE FROM ProcessedFile pf WHERE pf.createdAt < :cutoffDate AND pf.processingStatus IN :statuses")
  int deleteByCreatedAtBeforeAndProcessingStatusIn(
      @Param("cutoffDate") LocalDateTime cutoffDate,
      @Param("statuses") List<ProcessingStatus> statuses);
}
