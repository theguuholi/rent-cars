package com.github.theguuholi.rentcars.specifications;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SpecificationRequestTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void readsFieldsDirectlyFromTheRequestBody() {
    var request =
        mapper.readValue(
            """
        {"name":"gps","description":"Navigation"}
        """,
            SpecificationRequest.class);
    assertThat(request.name).isEqualTo("gps");
    assertThat(request.description).isEqualTo("Navigation");
  }

  @Test
  void distinguishesOmittedFieldsFromExplicitNullOnUpdate() {
    var omitted = mapper.readValue("{}", SpecificationRequest.class);
    assertThat(omitted.name).isNull();
    assertThat(omitted.description).isNull();
    var cleared =
        mapper.readValue("{\"name\":null,\"description\":null}", SpecificationRequest.class);
    assertThat(cleared.name).isEmpty();
    assertThat(cleared.description).isEmpty();
  }
}
