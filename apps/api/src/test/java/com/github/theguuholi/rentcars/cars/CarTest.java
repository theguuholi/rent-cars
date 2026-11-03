package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CarTest {
  private static final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
  private final Car car = new Car();
  private final UUID category = UUID.randomUUID();
  private final UUID spec = UUID.randomUUID();

  @AfterAll
  static void closeValidatorFactory() {
    validators.close();
  }

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
      assertThat(car.getName()).isEqualTo("Civic");
      assertThat(car.getDescription()).isEqualTo("Automatic sedan");
      assertThat(car.getBrand()).isEqualTo("Honda");
      assertThat(car.getLicensePlate()).isEqualTo("ABC1234");
      assertThat(car.isAvailable()).isTrue();
      assertThat(car.getDailyRateCents()).isEqualTo(100);
      assertThat(car.getFineAmountCents()).isZero();
      assertThat(car.getCategoryId()).isEqualTo(category);
      assertThat(car.getSpecificationIds()).containsExactly(spec);
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
      assertThat(car.getCategoryId()).isEqualTo(otherCategory);
      assertThat(car.getSpecificationIds()).containsExactly(otherSpec);
      var returnedSpecs = car.getSpecificationIds();
      assertThatThrownBy(returnedSpecs::clear).isInstanceOf(UnsupportedOperationException.class);
      assertThat(car.isAvailable()).isFalse();
      assertThat(car.getDailyRateCents()).isEqualTo(100);
    }

    @DisplayName(
        "Given an empty specification set, a patch clears the links and preserves the category and plate")
    @Test
    void givenEmptySpecificationsWhenPatchingThenClearsOnlyTheLinks() {
      car.apply(withText("Civic", "Automatic sedan", "Honda", "ABC1234"));
      car.apply(new CarRequest(null, null, null, "abc1234", null, null, null, null, Set.of()));
      assertThat(car.getSpecificationIds()).isEmpty();
      assertThat(car.getLicensePlate()).isEqualTo("ABC1234");
      assertThat(car.getCategoryId()).isEqualTo(category);
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
      assertThat(car.getLicensePlate()).isEqualTo("ABC1234");
    }
  }

  @Nested
  @DisplayName("Entity validation")
  class EntityValidation {
    @DisplayName("Given text at each maximum length, the car passes validation")
    @Test
    void givenMaximumLengthsWhenValidatingThenAcceptsCar() {
      car.apply(withText("n".repeat(100), "d".repeat(255), "b".repeat(80), "ß".repeat(10)));
      assertThat(car.getLicensePlate()).isEqualTo("S".repeat(20));
      assertThat(validators.getValidator().validate(car)).isEmpty();
    }

    @DisplayName("Given oversized text, validation reports the offending field")
    @ParameterizedTest
    @CsvSource({
      "101,255,80,20,name",
      "100,256,80,20,description",
      "100,255,81,20,brand",
      "100,255,80,21,licensePlate"
    })
    void givenOversizedTextWhenValidatingThenReportsField(
        int name, int description, int brand, int plate, String field) {
      car.apply(
          withText(
              "n".repeat(name), "d".repeat(description), "b".repeat(brand), "A".repeat(plate)));
      assertThat(validators.getValidator().validate(car))
          .extracting(v -> v.getPropertyPath().toString())
          .containsExactly(field);
    }

    @DisplayName(
        "Given a 20-character plate that expands to 21 in uppercase, validation rejects the normalized plate")
    @Test
    void givenExpandingPlateWhenValidatingThenRejectsNormalizedLength() {
      car.apply(withText("Civic", "Automatic sedan", "Honda", "a".repeat(19) + "ß"));
      assertThat(car.getLicensePlate()).hasSize(21);
      assertThat(validators.getValidator().validate(car))
          .extracting(v -> v.getPropertyPath().toString())
          .containsExactly("licensePlate");
    }
  }
}
