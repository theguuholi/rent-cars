package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class CarRequestDeserializerTest {
  private final JsonMapper json =
      JsonMapper.builder()
          .addModule(
              new SimpleModule().addDeserializer(CarRequest.class, new CarRequestDeserializer()))
          .build();

  @Nested
  @DisplayName("deserialize()")
  class Deserialize {
    @Test
    @DisplayName(
        "Given omitted attributes, when parsing, then they remain absent while false and zero are retained")
    void partialPatch() {
      var change = json.readValue("{\"available\":false,\"fineAmountCents\":0}", CarRequest.class);
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
      var car = json.treeToValue(body, CarRequest.class);
      assertThat(car.name()).isEqualTo("Civic");
      assertThat(car.description()).isEqualTo("Family car");
      assertThat(car.brand()).isEqualTo("Honda");
      assertThat(car.licensePlate()).isEqualTo(" abc ");
      assertThat(car.dailyRateCents()).isEqualTo(Integer.MAX_VALUE);
      assertThat(car.fineAmountCents()).isZero();
      assertThat(car.available()).isTrue();
      assertThat(car.categoryId()).isEqualTo(category);
      var selected = car.specificationIds();
      assertThat(selected).containsExactly(specification);
      assertThatThrownBy(selected::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName(
        "Given an empty specification list, when parsing, then the request explicitly clears assignments")
    void emptySelection() {
      var request = json.readValue("{\"specificationIds\":[]}", CarRequest.class);
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
      expectInvalidJson("{\"" + field + "\":null}");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "[]",
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
        "Given an invalid shape, type or identifier, when parsing, then the car request is rejected")
    void malformedAttributes(String body) {
      expectInvalidJson(body);
    }

    private void expectInvalidJson(String body) {
      assertThatThrownBy(() -> json.readValue(body, CarRequest.class))
          .isInstanceOf(JacksonException.class);
    }
  }
}
