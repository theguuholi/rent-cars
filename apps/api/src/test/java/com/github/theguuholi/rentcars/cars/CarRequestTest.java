package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class CarRequestTest {
  private final JsonMapper json = JsonMapper.builder().build();

  @Nested
  @DisplayName("CarRequest")
  class Constructor {
    @Test
    @DisplayName(
        "Given a mutable specification set, when constructing a request, then it retains an immutable snapshot")
    void givenMutableSpecificationIdsWhenConstructingThenCopiesTheSelection() {
      var specification = UUID.randomUUID();
      var selection = new HashSet<>(Set.of(specification));
      var request = new CarRequest(null, null, null, null, null, null, null, null, selection);
      selection.clear();
      assertThat(request.specificationIds()).containsExactly(specification);
      var returnedSpecs = request.specificationIds();
      assertThatThrownBy(returnedSpecs::clear).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("parse()")
  class Parse {
    @Test
    @DisplayName(
        "Given omitted attributes, when parsing, then they remain absent while false and zero are retained")
    void partialPatch() {
      var change = CarRequest.parse(json.readTree("{\"available\":false,\"fineAmountCents\":0}"));
      assertThat(change.available()).isFalse();
      assertThat(change.fineAmountCents()).isZero();
      assertThat(change.name()).isNull();
      assertThat(change.description()).isNull();
      assertThat(change.brand()).isNull();
      assertThat(change.licensePlate()).isNull();
      assertThat(change.dailyRateCents()).isNull();
      assertThat(change.categoryId()).isNull();
      assertThat(change.specificationIds()).isNull();
    }

    @Test
    @DisplayName(
        "Given all attributes, when parsing, then strings, integer cents and catalog IDs retain their values")
    void fullRequest() {
      var category = UUID.randomUUID();
      var specification = UUID.randomUUID();
      var body =
          json.readTree(
              """
          {"name":"Civic","description":"Family car","brand":"Honda","licensePlate":" abc ",
           "dailyRateCents":2147483647,"fineAmountCents":0,"available":true,
           "categoryId":"%s","specificationIds":["%s","%s"]}
          """
                  .formatted(category, specification, specification));
      var car = CarRequest.parse(body);
      assertThat(car.name()).isEqualTo("Civic");
      assertThat(car.description()).isEqualTo("Family car");
      assertThat(car.brand()).isEqualTo("Honda");
      assertThat(car.licensePlate()).isEqualTo(" abc ");
      assertThat(car.dailyRateCents()).isEqualTo(Integer.MAX_VALUE);
      assertThat(car.fineAmountCents()).isZero();
      assertThat(car.available()).isTrue();
      assertThat(car.categoryId()).isEqualTo(category);
      assertThat(car.specificationIds()).containsExactly(specification);
    }

    @Test
    @DisplayName(
        "Given an empty specification list, when parsing, then the request explicitly clears assignments")
    void emptySelection() {
      var request = CarRequest.parse(json.readTree("{\"specificationIds\":[]}"));
      assertThat(request.specificationIds()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "name",
          "description",
          "brand",
          "licensePlate",
          "available",
          "dailyRateCents",
          "fineAmountCents",
          "categoryId",
          "specificationIds"
        })
    @DisplayName("Given an explicit null, when parsing, then every supplied attribute rejects it")
    void explicitNull(String field) {
      expectBadRequest("{\"" + field + "\":null}");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "[]",
          "null",
          "42",
          "true",
          "\"car\"",
          "{\"extra\":1}",
          "{\"id\":\"server-owned\"}",
          "{\"name\":42}",
          "{\"brand\":true}",
          "{\"description\":1.75}",
          "{\"licensePlate\":{}}",
          "{\"specificationIds\":[null]}",
          "{\"specificationIds\":[\"bad-id\"]}",
          "{\"specificationIds\":{}}",
          "{\"categoryId\":\"unknown\"}",
          "{\"categoryId\":4}",
          "{\"available\":\"true\"}",
          "{\"available\":1}",
          "{\"dailyRateCents\":0.5}",
          "{\"dailyRateCents\":2147483648}",
          "{\"fineAmountCents\":\"0\"}",
          "{\"fineAmountCents\":false}"
        })
    @DisplayName(
        "Given an invalid shape, type or identifier, when parsing, then the request fails with 400")
    void malformedAttributes(String body) {
      expectBadRequest(body);
    }

    private void expectBadRequest(String body) {
      var tree = json.readTree(body);
      assertThatThrownBy(() -> CarRequest.parse(tree))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
  }
}
