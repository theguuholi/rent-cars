package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CarTest {
  private final Car car = new Car();
  private final UUID category = UUID.randomUUID();
  private final UUID spec = UUID.randomUUID();

  private CarRequest withText(String name, String description, String brand, String plate) {
    return new CarRequest(name, description, brand, plate, null, 100, 0, category, Set.of(spec));
  }

  @Nested
  @DisplayName("apply")
  class Apply {
    @DisplayName(
        "Given a complete car, applying its attributes sets the catalog details and defaults availability to true")
    @Test
    void givenCompleteCarWhenApplyingThenSetsDetailsAndDefaultAvailability() {
      car.apply(withText("Civic", "Automatic sedan", "Honda", " abc1234 "));
      assertThat(car.toResponse())
          .isEqualTo(
              new CarResponse(
                  null,
                  "Civic",
                  "Automatic sedan",
                  "Honda",
                  "ABC1234",
                  true,
                  100,
                  0,
                  category,
                  Set.of(spec)));
    }

    @DisplayName(
        "Given replacement assignments, the car takes the new category and keeps its own copy of the specifications")
    @Test
    void givenReplacementAssignmentsWhenPatchingThenReplacesCategoryAndCopiesSpecs() {
      car.apply(withText("Civic", "Automatic sedan", "Honda", "ABC1234"));
      var otherCategory = UUID.randomUUID();
      var otherSpec = UUID.randomUUID();
      var specs = new HashSet<>(Set.of(otherSpec));
      car.apply(new CarRequest(null, null, null, null, false, null, null, otherCategory, specs));
      specs.clear();
      var response = car.toResponse();
      assertThat(response.categoryId()).isEqualTo(otherCategory);
      assertThat(response.specificationIds()).containsExactly(otherSpec);
      assertThat(response.available()).isFalse();
      assertThat(response.dailyRateCents()).isEqualTo(100);
    }

    @DisplayName(
        "Given an empty specification set, a patch clears the links and preserves the category and plate")
    @Test
    void givenEmptySpecificationsWhenPatchingThenClearsOnlyTheLinks() {
      car.apply(withText("Civic", "Automatic sedan", "Honda", "ABC1234"));
      car.apply(new CarRequest(null, null, null, "abc1234", null, null, null, null, Set.of()));
      var response = car.toResponse();
      assertThat(response.specificationIds()).isEmpty();
      assertThat(response.licensePlate()).isEqualTo("ABC1234");
      assertThat(response.categoryId()).isEqualTo(category);
    }

    @DisplayName(
        "Given an existing plate, a different plate is rejected and the original is retained")
    @Test
    void givenChangedPlateWhenPatchingThenRejectsTheNewPlate() {
      car.apply(withText("Civic", "Automatic sedan", "Honda", "ABC1234"));
      var change = new CarRequest(null, null, null, "DIFFERENT", null, null, null, null, null);
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> car.apply(change))
          .satisfies(
              ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
      assertThat(car.toResponse().licensePlate()).isEqualTo("ABC1234");
    }
  }

  @Nested
  @DisplayName("toResponse")
  class ToResponse {
    @DisplayName(
        "Given a car with specifications, its response keeps an immutable snapshot after a patch")
    @Test
    void givenCarWhenTakingSnapshotThenSpecificationsStayImmutable() {
      car.apply(withText("Civic", "Automatic sedan", "Honda", "ABC1234"));
      var response = car.toResponse();
      car.apply(new CarRequest(null, null, null, null, null, null, null, null, Set.of()));
      assertThat(response.specificationIds()).containsExactly(spec);
      assertThatThrownBy(response.specificationIds()::clear)
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }
}
