package com.reviewsystem.repository;

import com.reviewsystem.domain.entity.Provider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

  Optional<Provider> findByCode(String code);

  boolean existsByCode(String code);

  List<Provider> findByActive(Boolean active);

  @Query("SELECT p FROM Provider p WHERE p.active = true ORDER BY p.name")
  List<Provider> findAllActiveProviders();

  @Query("SELECT p FROM Provider p WHERE UPPER(p.name) LIKE UPPER(CONCAT('%', :name, '%'))")
  List<Provider> findByNameContainingIgnoreCase(@Param("name") String name);
}
