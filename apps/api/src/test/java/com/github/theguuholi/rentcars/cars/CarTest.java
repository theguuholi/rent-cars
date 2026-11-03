package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CarTest {
  private final JsonMapper json = JsonMapper.builder().build();
  private final Car car = new Car();
  private final UUID category = UUID.randomUUID();
  private final UUID spec = UUID.randomUUID();

  private CarRequest civic(String plate) {
    var request = new CarRequest();
    request.name = "Civic";
    request.description = "Automatic sedan";
    request.brand = "Honda";
    request.licensePlate = plate;
    request.dailyRateCents = 100;
    request.fineAmountCents = 0;
    request.categoryId = category;
    request.specificationIds = Set.of(spec);
    return request;
  }

  @Nested
  @DisplayName("apply")
  class Apply {
    @DisplayName(
        "Given a complete car, applying its attributes sets the catalog details and defaults availability to true")
    @Test
    void givenCompleteCarWhenApplyingThenSetsDetailsAndDefaultAvailability() {
      car.apply(civic(" abc1234 "));
      JsonNode response = json.valueToTree(car);
      assertThat(response)
          .isEqualTo(
              json.readTree(
                  """
              {
                "id": null, "name": "Civic", "description": "Automatic sedan", "brand": "Honda",
                "licensePlate": "ABC1234", "available": true, "dailyRateCents": 100,
                "fineAmountCents": 0, "categoryId": "%s", "specificationIds": ["%s"]
              }
              """
                      .formatted(category, spec)));
    }

    @DisplayName(
        "Given replacement assignments, the car takes the new category and keeps its own copy of the specifications")
    @Test
    void givenReplacementAssignmentsWhenPatchingThenReplacesCategoryAndCopiesSpecs() {
      car.apply(civic("ABC1234"));
      var otherCategory = UUID.randomUUID();
      var otherSpec = UUID.randomUUID();
      var specs = new HashSet<>(Set.of(otherSpec));
      var patch = new CarRequest();
      patch.available = false;
      patch.categoryId = otherCategory;
      patch.specificationIds = specs;
      car.apply(patch);
      specs.clear();
      JsonNode response = json.valueToTree(car);
      assertThat(response.get("categoryId").asString()).isEqualTo(otherCategory.toString());
      assertThat(response.get("specificationIds")).isEqualTo(json.valueToTree(Set.of(otherSpec)));
      assertThat(response.get("available").asBoolean()).isFalse();
      assertThat(response.get("dailyRateCents").asInt()).isEqualTo(100);
    }

    @DisplayName(
        "Given an empty specification set, a patch clears the links and preserves the category and plate")
    @Test
    void givenEmptySpecificationsWhenPatchingThenClearsOnlyTheLinks() {
      car.apply(civic("ABC1234"));
      var patch = new CarRequest();
      patch.licensePlate = "abc1234";
      patch.specificationIds = Set.of();
      car.apply(patch);
      JsonNode response = json.valueToTree(car);
      assertThat(response.get("specificationIds")).isEqualTo(json.createArrayNode());
      assertThat(response.get("licensePlate").asString()).isEqualTo("ABC1234");
      assertThat(response.get("categoryId").asString()).isEqualTo(category.toString());
    }

    @DisplayName(
        "Given an existing plate, a different plate is rejected and the original is retained")
    @Test
    void givenChangedPlateWhenPatchingThenRejectsTheNewPlate() {
      car.apply(civic("ABC1234"));
      var change = new CarRequest();
      change.licensePlate = "DIFFERENT";
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> car.apply(change))
          .satisfies(
              ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
      assertThat(json.valueToTree(car).get("licensePlate").asString()).isEqualTo("ABC1234");
    }
  }
}
