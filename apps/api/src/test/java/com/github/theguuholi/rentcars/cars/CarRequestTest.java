package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class CarRequestTest {
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void givenPartialPatchWhenParsingThenAbsentAttributesRemainAbsent() {
    var change = CarRequest.parse(json.readTree("{\"available\":false,\"fineAmountCents\":0}"));
    assertThat(change.available).isFalse();
    assertThat(change.fineAmountCents).isZero();
    assertThat(change.name).isNull();
    assertThat(change.categoryId).isNull();
    assertThat(change.specificationIds).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "null",
        "42",
        "{\"name\":42}",
        "{\"brand\":true}",
        "{\"description\":1.75}",
        "{\"specificationIds\":[null]}",
        "{\"categoryId\":\"unknown\"}",
        "{\"available\":\"yes\"}"
      })
  void givenIncompatibleJsonTypesWhenParsingThenRejectsThePayload(String body) {
    assertThatThrownBy(() -> CarRequest.parse(json.readTree(body)))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
  }
}
