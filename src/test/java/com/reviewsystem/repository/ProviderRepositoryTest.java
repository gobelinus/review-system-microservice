package com.reviewsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reviewsystem.common.enums.ProviderType;
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
@ActiveProfiles("test-postgres")
@DisplayName("providerRepository Unit Tests")
class ProviderRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private ProviderRepository providerRepository;

  private Provider testprovider;

  @BeforeEach
  void setUp() {
    providerRepository.deleteAll();
    testprovider =
        Provider.builder()
            .code(ProviderType.AGODA)
            .name("Agoda.com")
            .description("Agoda travel provider")
            .active(true)
            .build();
  }

  @Test
  @DisplayName("Should save and find provider by code")
  void shouldSaveAndFindproviderByCode() {
    // When
    Provider savedprovider = providerRepository.save(testprovider);
    Optional<Provider> foundprovider = providerRepository.findByCode("AGODA");

    // Then
    assertThat(savedprovider.getId()).isNotNull();
    assertThat(foundprovider).isPresent();
    assertThat(foundprovider.get().getName()).isEqualTo("Agoda.com");
    assertThat(foundprovider.get().getDescription()).isEqualTo("Agoda travel provider");
  }

  @Test
  @DisplayName("Should check existence by code")
  void shouldCheckExistenceByCode() {
    // Given
    providerRepository.save(testprovider);

    // When
    boolean exists = providerRepository.existsByCode("AGODA");
    boolean notExists = providerRepository.existsByCode("NONEXISTENT");

    // Then
    assertThat(exists).isTrue();
    assertThat(notExists).isFalse();
  }

  @Test
  @DisplayName("Should find providers by active status")
  void shouldFindprovidersByActiveStatus() {
    // Given
    Provider activeprovider =
        Provider.builder().code(ProviderType.BOOKING).name("Booking.com").active(true).build();
    Provider inactiveprovider =
        Provider.builder().code(ProviderType.EXPEDIA).name("Expedia.com").active(false).build();
    providerRepository.save(testprovider); // Active by default
    providerRepository.save(activeprovider);
    providerRepository.save(inactiveprovider);

    // When
    List<Provider> activeproviders = providerRepository.findByActive(true);
    List<Provider> inactiveproviders = providerRepository.findByActive(false);

    // Then
    assertThat(activeproviders).hasSize(2);
    assertThat(inactiveproviders).hasSize(1);
    assertThat(inactiveproviders.get(0).getCode()).isEqualTo("EXPEDIA");
  }

  @Test
  @DisplayName("Should find all active providers ordered by name")
  void shouldFindAllActiveprovidersOrderedByName() {
    // Given
    Provider bookingprovider =
        Provider.builder().code(ProviderType.BOOKING).name("Booking.com").active(true).build();
    Provider expediaprovider =
        Provider.builder().code(ProviderType.EXPEDIA).name("Expedia.com").active(true).build();
    Provider inactiveprovider =
        Provider.builder()
            .code(ProviderType.INACTIVE)
            .name("Inactive provider")
            .active(false)
            .build();

    providerRepository.save(expediaprovider); // Should be first alphabetically
    providerRepository.save(testprovider); // Agoda.com
    providerRepository.save(bookingprovider); // Booking.com
    providerRepository.save(inactiveprovider); // Should be excluded

    // When
    List<Provider> activeproviders = providerRepository.findAllActiveProviders();

    // Then
    assertThat(activeproviders).hasSize(3);
    assertThat(activeproviders.get(0).getName()).isEqualTo("Agoda.com");
    assertThat(activeproviders.get(1).getName()).isEqualTo("Booking.com");
    assertThat(activeproviders.get(2).getName()).isEqualTo("Expedia.com");
  }

  @Test
  @DisplayName("Should find providers by name containing ignore case")
  void shouldFindprovidersByNameContainingIgnoreCase() {
    // Given
    Provider bookingprovider =
        Provider.builder().code(ProviderType.BOOKING).name("Booking.com").active(true).build();
    Provider airbnbprovider =
        Provider.builder().code(ProviderType.AIRBNB).name("Airbnb Inc").active(true).build();

    providerRepository.save(testprovider);
    providerRepository.save(bookingprovider);
    providerRepository.save(airbnbprovider);

    // When
    List<Provider> dotComproviders = providerRepository.findByNameContainingIgnoreCase(".com");
    List<Provider> bookingproviders = providerRepository.findByNameContainingIgnoreCase("BOOKING");

    // Then
    assertThat(dotComproviders).hasSize(2);
    assertThat(bookingproviders).hasSize(1);
    assertThat(bookingproviders.get(0).getCode()).isEqualTo("BOOKING");
  }

  @Test
  @DisplayName("Should validate provider constraints")
  void shouldValidateproviderConstraints() {
    // When & Then - Missing code should throw ConstraintViolationException
    assertThatThrownBy(
            () -> {
              Provider invalidprovider = new Provider();
              invalidprovider.setName("Valid Name");
              providerRepository.saveAndFlush(invalidprovider);
            })
        .isInstanceOf(ConstraintViolationException.class);

    // Missing name should throw ConstraintViolationException
    assertThatThrownBy(
            () -> {
              Provider invalidprovider = new Provider();
              invalidprovider.setCode(ProviderType.INACTIVE);
              invalidprovider.setName(""); // Blank name
              providerRepository.saveAndFlush(invalidprovider);
            })
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("Should handle unique constraint on provider code")
  void shouldHandleUniqueConstraintOnproviderCode() {
    // Given
    providerRepository.save(testprovider);

    // When & Then - Duplicate code should fail
    Provider duplicateprovider =
        Provider.builder()
            .code(ProviderType.AGODA)
            .name("Agoda.com")
            .description("Agoda travel provider")
            .active(true)
            .build();

    assertThatThrownBy(() -> providerRepository.saveAndFlush(duplicateprovider))
        .hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class);
  }
}
