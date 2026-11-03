package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class CarServiceTest {
  private static final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
  private final CarRepository repository = mock(CarRepository.class);
  private final CarService service = new CarService(repository, validators.getValidator());

  @AfterAll
  static void closeValidatorFactory() {
    validators.close();
  }

  @Test
  void givenMatchingNamesWhenListingThenUsesIdAsStableTieBreaker() {
    var rows = List.of(new Car(), new Car());
    when(repository.findAll(Sort.by("name", "id"))).thenReturn(rows);
    assertThat(service.list()).containsExactlyElementsOf(rows);
  }

  @Test
  void givenUnknownCarWhenUpdatingThenReportsNotFoundBeforeAttributeValidation() {
    var id = UUID.randomUUID();
    when(repository.findForUpdate(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(id, new CarRequest()))
        .isInstanceOfSatisfying(
            org.springframework.web.server.ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void givenIncompleteCarWhenCreatingThenReportsAllMissingFieldsBeforePersistence() {
    assertThatThrownBy(() -> service.create(new CarRequest()))
        .isInstanceOfSatisfying(
            ConstraintViolationException.class,
            ex ->
                assertThat(ex.getConstraintViolations())
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder(
                        "name",
                        "brand",
                        "description",
                        "licensePlate",
                        "categoryId",
                        "dailyRateCents",
                        "fineAmountCents"));
    verifyNoInteractions(repository);
  }
}
