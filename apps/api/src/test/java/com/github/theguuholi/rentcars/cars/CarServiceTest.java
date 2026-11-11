package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CarServiceTest {
  private static final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
  private final CarRepository repository = mock();
  private final CarService service =
      new CarService(
          repository,
          validators.getValidator(),
          mock(com.github.theguuholi.rentcars.shared.storage.ImageStorage.class),
          mock(jakarta.persistence.EntityManager.class));
  private final UUID id = UUID.randomUUID();

  private static CarRequest validCar() {
    var request = patchName("Civic");
    request.description = "Sedan";
    request.brand = "Honda";
    request.licensePlate = " abc1234 ";
    request.dailyRateCents = 100;
    request.fineAmountCents = 0;
    request.categoryId = UUID.randomUUID();
    return request;
  }

  private static CarRequest patchName(String name) {
    var request = new CarRequest();
    request.expectedVersion = 0L;
    request.name = name;
    return request;
  }

  @AfterAll
  static void closeValidatorFactory() {
    validators.close();
  }

  @Nested
  @DisplayName("list")
  class ListCars {
    @DisplayName(
        "Given filters, when listing the public fleet, then ID breaks ties and only available cars are queried")
    @Test
    void givenMatchingNamesWhenListingThenUsesIdAsTieBreaker() {
      var rows = List.of(new Car(), new Car());
      var query = PageRequest.of(0, 12, Sort.by("name", "id"));
      when(repository.search(true, "%argo%", "%fiat%", "%hatchback%", query))
          .thenReturn(new PageImpl<>(rows, query, 2));
      assertThat(
              service
                  .list(true, " Argo ", "Fiat", "HATCHBACK", PageRequest.of(0, 12, Sort.by("name")))
                  .content())
          .containsExactlyElementsOf(rows);
    }

    @DisplayName("Given an empty catalog, when listing all cars, then blank filters match all rows")
    @Test
    void givenEmptyCatalogWhenListingThenReturnsNoCars() {
      var query = PageRequest.of(1, 5, Sort.by("brand", "id"));
      when(repository.search(false, "%", "%", "%", query))
          .thenReturn(new PageImpl<>(List.of(), query, 0));
      assertThat(
              service.list(false, " ", null, "", PageRequest.of(1, 5, Sort.by("brand"))).content())
          .isEmpty();
    }

    @Test
    @DisplayName("Given an excessive page size, when listing, then the request is rejected")
    void invalidSize() {
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> service.list(true, null, null, null, PageRequest.of(0, 101)))
          .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
      Pageable invalid = mock(Pageable.class);
      when(invalid.getPageSize()).thenReturn(0);
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> service.list(true, null, null, null, invalid))
          .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Given an unsupported sort, when listing, then the request is rejected")
    void invalidSort() {
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(
              () ->
                  service.list(
                      true, null, null, null, PageRequest.of(0, 12, Sort.by("licensePlate"))))
          .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName(
        "Given wildcard characters in a name, when searching, then they are treated as text")
    void escapesWildcards() {
      var query = PageRequest.of(0, 12, Sort.by("name", "id"));
      when(repository.search(true, "%a!%!_!!%", "%", "%", query))
          .thenReturn(new PageImpl<>(List.of(), query, 0));
      assertThat(service.list(true, "A%_!", null, null, PageRequest.of(0, 12)).content()).isEmpty();
    }

    @Test
    @DisplayName("Given ID as the requested sort, when listing, then it is not duplicated")
    void preservesIdSort() {
      var query = PageRequest.of(0, 12, Sort.by(Sort.Order.desc("id")));
      when(repository.search(true, "%", "%", "%", query))
          .thenReturn(new PageImpl<>(List.of(), query, 0));
      assertThat(service.list(true, null, null, null, query).content()).isEmpty();
      verify(repository).search(true, "%", "%", "%", query);
    }
  }

  @Nested
  @DisplayName("get")
  class Get {
    @DisplayName("Given an existing ID, when fetching it, then the matching car is returned")
    @Test
    void givenExistingIdWhenFetchingThenReturnsTheCar() {
      var car = new Car();
      when(repository.findDetailedById(id)).thenReturn(Optional.of(car));
      assertThat(service.get(id)).isSameAs(car);
    }

    @DisplayName("Given an unknown ID, when fetching it, then the service reports not found")
    @Test
    void givenUnknownIdWhenFetchingThenReportsNotFound() {
      when(repository.findDetailedById(id)).thenReturn(Optional.empty());
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> service.get(id))
          .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("create")
  class Create {
    @DisplayName(
        "Given a valid request, when creating a car, then the service returns the persisted entity")
    @Test
    void givenValidCarWhenCreatingThenReturnsSavedCar() {
      var persisted = new Car();
      when(repository.saveAndFlush(any(Car.class))).thenReturn(persisted);
      assertThat(service.create(validCar())).isSameAs(persisted);
    }

    @DisplayName(
        "Given an incomplete request, when creating a car, then all missing fields are reported before saving")
    @Test
    void givenMissingFieldsWhenCreatingThenReportsAllViolations() {
      var incomplete = new CarRequest();
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
        "Given a plate that expands beyond 20 characters in uppercase, when creating a car, then the normalized plate is rejected")
    @Test
    void givenExpandingPlateWhenCreatingThenRejectsNormalizedLength() {
      var request = validCar();
      request.licensePlate = "a".repeat(19) + "ß";
      assertThatExceptionOfType(ConstraintViolationException.class)
          .isThrownBy(() -> service.create(request))
          .satisfies(
              ex ->
                  assertThat(ex.getConstraintViolations())
                      .extracting(v -> v.getPropertyPath().toString())
                      .containsExactly("licensePlate"));
      verifyNoInteractions(repository);
    }

    @DisplayName(
        "Given a negative rate, when creating a car, then the service rejects the request before building the car")
    @Test
    void givenNegativeRateWhenCreatingThenRejectsTheRequest() {
      var negative = new CarRequest();
      negative.dailyRateCents = -1;
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
        "Given a partial patch with the current version, when updating the fleet, then the service returns the saved car")
    @Test
    void givenCurrentVersionWhenUpdatingThenSavesCar() {
      var car = new Car();
      car.apply(validCar());
      when(repository.findById(id)).thenReturn(Optional.of(car));
      when(repository.saveAndFlush(car)).thenReturn(car);
      assertThat(service.update(id, patchName("Accord"))).isSameAs(car);
      verify(repository).saveAndFlush(car);
    }

    @DisplayName(
        "Given a blank replacement name, when updating the car, then the service reports the invalid field without saving")
    @Test
    void givenInvalidPatchWhenUpdatingThenRejectsWithoutSaving() {
      var car = new Car();
      car.apply(validCar());
      when(repository.findById(id)).thenReturn(Optional.of(car));
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

    @DisplayName(
        "Given an unknown ID, when updating a car, then the service reports not found before validating the patch")
    @Test
    void givenUnknownIdWhenUpdatingThenReportsNotFoundBeforeValidation() {
      when(repository.findById(id)).thenReturn(Optional.empty());
      var invalid = new CarRequest();
      invalid.dailyRateCents = -1;
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> service.update(id, invalid))
          .satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
      verify(repository, never()).saveAndFlush(any());
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {
    @DisplayName("Given an existing ID, when deleting it, then the service removes the locked car")
    @Test
    void givenExistingIdWhenDeletingThenDeletesTheLockedCar() {
      var car = new Car();
      when(repository.findForUpdate(id)).thenReturn(Optional.of(car));
      service.delete(id);
      verify(repository).delete(car);
    }

    @DisplayName(
        "Given an unknown ID, when deleting it, then the service reports not found without writing")
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
