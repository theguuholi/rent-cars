package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CarServiceTest {
  private static final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
  private final CarRepository repository = mock();
  private final CarService service = new CarService(repository, validators.getValidator());
  private final UUID id = UUID.randomUUID();
  private final CarRequest valid =
      new CarRequest(
          "Civic",
          "Sedan",
          "Honda",
          " abc1234 ",
          false,
          100,
          0,
          UUID.randomUUID(),
          Set.of(UUID.randomUUID()));

  private static CarRequest patchName(String name) {
    return new CarRequest(name, null, null, null, null, null, null, null, null);
  }

  @AfterAll
  static void closeValidatorFactory() {
    validators.close();
  }

  @Nested
  @DisplayName("list")
  class ListCars {
    @DisplayName("Given cars with matching names, listing requests ID as the stable tie-breaker")
    @Test
    void givenMatchingNamesWhenListingThenUsesIdAsTieBreaker() {
      var rows = List.of(new Car(), new Car());
      when(repository.findAll(Sort.by("name", "id"))).thenReturn(rows);
      assertThat(service.list()).containsExactlyElementsOf(rows);
    }

    @DisplayName("Given an empty catalog, listing returns no cars")
    @Test
    void givenEmptyCatalogWhenListingThenReturnsNoCars() {
      when(repository.findAll(Sort.by("name", "id"))).thenReturn(List.of());
      assertThat(service.list()).isEmpty();
    }
  }

  @Nested
  @DisplayName("get")
  class Get {
    @DisplayName("Given an existing ID, fetching it returns that car")
    @Test
    void givenExistingIdWhenFetchingThenReturnsTheCar() {
      var car = new Car();
      when(repository.findById(id)).thenReturn(Optional.of(car));
      assertThat(service.get(id)).isSameAs(car);
    }

    @DisplayName("Given an unknown ID, fetching it reports not found")
    @Test
    void givenUnknownIdWhenFetchingThenReportsNotFound() {
      when(repository.findById(id)).thenReturn(Optional.empty());
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> service.get(id))
          .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("create")
  class Create {
    @DisplayName(
        "Given a valid request, creating flushes the normalized car with its category and specifications")
    @Test
    void givenValidCarWhenCreatingThenFlushesNormalizedAssignments() {
      when(repository.saveAndFlush(any(Car.class))).thenAnswer(call -> call.getArgument(0));
      var created = service.create(valid);
      verify(repository).saveAndFlush(created);
      var response = created.toResponse();
      assertThat(response.licensePlate()).isEqualTo("ABC1234");
      assertThat(response.categoryId()).isEqualTo(valid.categoryId());
      assertThat(response.specificationIds()).isEqualTo(valid.specificationIds());
    }

    @DisplayName("Given an incomplete request, creating reports all missing fields before saving")
    @Test
    void givenMissingFieldsWhenCreatingThenReportsAllViolations() {
      var incomplete = patchName(null);
      assertThatThrownBy(() -> service.create(incomplete))
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

    @DisplayName(
        "Given a plate that expands beyond 20 characters in uppercase, creating rejects the normalized plate")
    @Test
    void givenExpandingPlateWhenCreatingThenRejectsNormalizedLength() {
      var request =
          new CarRequest(
              "Civic",
              "Sedan",
              "Honda",
              "a".repeat(19) + "ß",
              true,
              100,
              0,
              valid.categoryId(),
              Set.of());
      assertThatExceptionOfType(ConstraintViolationException.class)
          .isThrownBy(() -> service.create(request))
          .satisfies(
              ex ->
                  assertThat(ex.getConstraintViolations())
                      .extracting(v -> v.getPropertyPath().toString())
                      .containsExactly("licensePlate"));
      verifyNoInteractions(repository);
    }

    @DisplayName("Given a negative rate, creating rejects the request before building the car")
    @Test
    void givenNegativeRateWhenCreatingThenRejectsTheRequest() {
      var negative = new CarRequest(null, null, null, null, null, -1, null, null, null);
      assertThatExceptionOfType(ConstraintViolationException.class)
          .isThrownBy(() -> service.create(negative))
          .satisfies(
              ex ->
                  assertThat(ex.getConstraintViolations())
                      .extracting(v -> v.getPropertyPath().toString())
                      .containsExactly("dailyRateCents"));
      verifyNoInteractions(repository);
    }
  }

  @Nested
  @DisplayName("update")
  class Update {
    @DisplayName(
        "Given a partial patch, updating saves the locked car and preserves omitted assignments")
    @Test
    void givenPartialPatchWhenUpdatingThenPreservesOmittedFields() {
      var car = new Car();
      car.apply(valid);
      when(repository.findForUpdate(id)).thenReturn(Optional.of(car));
      when(repository.saveAndFlush(car)).thenReturn(car);
      assertThat(service.update(id, patchName("Accord"))).isSameAs(car);
      var response = car.toResponse();
      assertThat(response.name()).isEqualTo("Accord");
      assertThat(response.categoryId()).isEqualTo(valid.categoryId());
      assertThat(response.specificationIds()).isEqualTo(valid.specificationIds());
    }

    @DisplayName(
        "Given a blank replacement name, updating reports the invalid field without saving")
    @Test
    void givenInvalidPatchWhenUpdatingThenRejectsWithoutSaving() {
      var car = new Car();
      car.apply(valid);
      when(repository.findForUpdate(id)).thenReturn(Optional.of(car));
      var blankName = patchName(" ");
      assertThatExceptionOfType(ConstraintViolationException.class)
          .isThrownBy(() -> service.update(id, blankName))
          .satisfies(
              ex ->
                  assertThat(ex.getConstraintViolations())
                      .extracting(v -> v.getPropertyPath().toString())
                      .containsExactly("name"));
      verify(repository, never()).saveAndFlush(any());
    }

    @DisplayName("Given an unknown ID, updating reports not found before validating the patch")
    @Test
    void givenUnknownIdWhenUpdatingThenReportsNotFoundBeforeValidation() {
      when(repository.findForUpdate(id)).thenReturn(Optional.empty());
      var invalid = new CarRequest(null, null, null, null, null, -1, null, null, null);
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> service.update(id, invalid))
          .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
      verify(repository, never()).saveAndFlush(any());
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {
    @DisplayName("Given an existing ID, deleting removes the locked car")
    @Test
    void givenExistingIdWhenDeletingThenDeletesTheLockedCar() {
      var car = new Car();
      when(repository.findForUpdate(id)).thenReturn(Optional.of(car));
      service.delete(id);
      verify(repository).delete(car);
    }

    @DisplayName("Given an unknown ID, deleting reports not found without writing")
    @Test
    void givenUnknownIdWhenDeletingThenReportsNotFoundWithoutWriting() {
      when(repository.findForUpdate(id)).thenReturn(Optional.empty());
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> service.delete(id))
          .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
      verify(repository, never()).delete(any());
    }
  }
}
