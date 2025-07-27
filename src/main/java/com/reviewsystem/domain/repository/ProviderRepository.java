package com.reviewsystem.domain.repository;

import com.reviewsystem.common.enums.ProviderType;
import com.reviewsystem.domain.entity.Provider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

  /** Find provider by name (case-sensitive) */
  Optional<Provider> findByName(String name);

  /** Find provider by name (case-insensitive) */
  @Query("SELECT p FROM provider p WHERE LOWER(p.name) = LOWER(:name)")
  Optional<Provider> findByNameIgnoreCase(@Param("name") String name);

  /** Find provider by type */
  Optional<Provider> findByType(ProviderType type);

  /** Find all providers by type */
  List<Provider> findAllByType(ProviderType type);

  /** Check if provider exists by name */
  boolean existsByName(String name);

  /** Check if provider exists by type */
  boolean existsByType(ProviderType type);

  /** Find all active providers */
  @Query("SELECT p FROM provider p WHERE p.isActive = true")
  List<Provider> findAllActive();

  /** Find providers by name pattern */
  @Query("SELECT p FROM provider p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :namePattern, '%'))")
  List<Provider> findByNameContainingIgnoreCase(@Param("namePattern") String namePattern);

  /** Count total providers */
  @Query("SELECT COUNT(p) FROM provider p")
  Long countAllproviders();

  /** Count active providers */
  @Query("SELECT COUNT(p) FROM provider p WHERE p.isActive = true")
  Long countActiveproviders();

  /** Find provider by external ID (if applicable) */
  @Query("SELECT p FROM provider p WHERE p.externalId = :externalId")
  Optional<Provider> findByExternalId(@Param("externalId") String externalId);
}
