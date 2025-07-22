package com.reviewsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reviewsystem.domain.entity.Provider;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ProviderRepository Unit Tests")
class ProviderRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private ProviderRepository providerRepository;

  private Provider testProvider;

  @BeforeEach
  void setUp() {
    providerRepository.deleteAll();
    testProvider =
        Provider.builder()
            .code("AGODA")
            .name("Agoda.com")
            .description("Agoda travel platform")
            .active(true)
            .build();
  }

  @Test
  @DisplayName("Should save and find provider by code")
  void shouldSaveAndFindProviderByCode() {
    // When
    Provider savedProvider = providerRepository.save(testProvider);
    Optional<Provider> foundProvider = providerRepository.findByCode("AGODA");

    // Then
    assertThat(savedProvider.getId()).isNotNull();
    assertThat(foundProvider).isPresent();
    assertThat(foundProvider.get().getName()).isEqualTo("Agoda.com");
    assertThat(foundProvider.get().getDescription()).isEqualTo("Agoda travel platform");
  }

  @Test
  @DisplayName("Should check existence by code")
  void shouldCheckExistenceByCode() {
    // Given
    providerRepository.save(testProvider);

    // When
    boolean exists = providerRepository.existsByCode("AGODA");
    boolean notExists = providerRepository.existsByCode("NONEXISTENT");

    // Then
    assertThat(exists).isTrue();
    assertThat(notExists).isFalse();
  }

  @Test
  @DisplayName("Should find providers by active status")
  void shouldFindProvidersByActiveStatus() {
    // Given
    Provider activeProvider =
        Provider.builder().code("BOOKING").name("Booking.com").active(true).build();
    Provider inactiveProvider =
        Provider.builder().code("EXPEDIA").name("Expedia.com").active(false).build();
    providerRepository.save(testProvider); // Active by default
    providerRepository.save(activeProvider);
    providerRepository.save(inactiveProvider);

    // When
    List<Provider> activeProviders = providerRepository.findByActive(true);
    List<Provider> inactiveProviders = providerRepository.findByActive(false);

    // Then
    assertThat(activeProviders).hasSize(2);
    assertThat(inactiveProviders).hasSize(1);
    assertThat(inactiveProviders.get(0).getCode()).isEqualTo("EXPEDIA");
  }

  @Test
  @DisplayName("Should find all active providers ordered by name")
  void shouldFindAllActiveProvidersOrderedByName() {
    // Given
    Provider bookingProvider =
        Provider.builder().code("BOOKING").name("Booking.com").active(true).build();
    Provider expediaProvider =
        Provider.builder().code("EXPEDIA").name("Expedia.com").active(true).build();
    Provider inactiveProvider =
        Provider.builder().code("INACTIVE").name("Inactive Provider").active(false).build();

    providerRepository.save(expediaProvider); // Should be first alphabetically
    providerRepository.save(testProvider); // Agoda.com
    providerRepository.save(bookingProvider); // Booking.com
    providerRepository.save(inactiveProvider); // Should be excluded

    // When
    List<Provider> activeProviders = providerRepository.findAllActiveProviders();

    // Then
    assertThat(activeProviders).hasSize(3);
    assertThat(activeProviders.get(0).getName()).isEqualTo("Agoda.com");
    assertThat(activeProviders.get(1).getName()).isEqualTo("Booking.com");
    assertThat(activeProviders.get(2).getName()).isEqualTo("Expedia.com");
  }

  @Test
  @DisplayName("Should find providers by name containing ignore case")
  void shouldFindProvidersByNameContainingIgnoreCase() {
    // Given
    Provider bookingProvider =
        Provider.builder().code("BOOKING").name("Booking.com").active(true).build();
    Provider airbnbProvider =
        Provider.builder().code("AIRBNB").name("Airbnb Inc").active(true).build();

    providerRepository.save(testProvider);
    providerRepository.save(bookingProvider);
    providerRepository.save(airbnbProvider);

    // When
    List<Provider> dotComProviders = providerRepository.findByNameContainingIgnoreCase(".com");
    List<Provider> bookingProviders = providerRepository.findByNameContainingIgnoreCase("BOOKING");

    // Then
    assertThat(dotComProviders).hasSize(2);
    assertThat(bookingProviders).hasSize(1);
    assertThat(bookingProviders.get(0).getCode()).isEqualTo("BOOKING");
  }

  @Test
  @DisplayName("Should validate provider constraints")
  void shouldValidateProviderConstraints() {
    // When & Then - Missing code should throw ConstraintViolationException
    assertThatThrownBy(
            () -> {
              Provider invalidProvider = new Provider();
              invalidProvider.setCode(""); // Blank code
              invalidProvider.setName("Valid Name");
              providerRepository.saveAndFlush(invalidProvider);
            })
        .isInstanceOf(ConstraintViolationException.class);

    // Missing name should throw ConstraintViolationException
    assertThatThrownBy(
            () -> {
              Provider invalidProvider = new Provider();
              invalidProvider.setCode("VALID");
              invalidProvider.setName(""); // Blank name
              providerRepository.saveAndFlush(invalidProvider);
            })
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("Should handle unique constraint on provider code")
  void shouldHandleUniqueConstraintOnProviderCode() {
    // Given
    providerRepository.save(testProvider);

    // When & Then - Duplicate code should fail
    Provider duplicateProvider =
        Provider.builder()
            .code("AGODA")
            .name("Agoda.com")
            .description("Agoda travel platform")
            .active(true)
            .build();

    assertThatThrownBy(() -> providerRepository.saveAndFlush(duplicateProvider))
        .hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class);
  }
}
