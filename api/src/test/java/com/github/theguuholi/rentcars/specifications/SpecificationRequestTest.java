package com.github.theguuholi.rentcars.specifications;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SpecificationRequestTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void missingEnvelopeFailsValidation() {
    var request = mapper.readValue("{}", SpecificationRequest.class);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request))
          .singleElement()
          .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("specification"));
    }
  }

  @Test
  void distinguishesOmittedFieldsFromExplicitNullOnUpdate() {
    var omitted =
        mapper.readValue("{\"specification\":{}}", SpecificationRequest.class).specification();
    assertThat(omitted.name).isNull();
    assertThat(omitted.description).isNull();
    var cleared =
        mapper
            .readValue(
                "{\"specification\":{\"name\":null,\"description\":null}}",
                SpecificationRequest.class)
            .specification();
    assertThat(cleared.name).isEmpty();
    assertThat(cleared.description).isEmpty();
  }
}
