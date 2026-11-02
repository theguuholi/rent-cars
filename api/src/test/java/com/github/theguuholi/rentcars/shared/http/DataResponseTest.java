package com.github.theguuholi.rentcars.shared.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DataResponseTest {
  @Test
  void serializesEmptyCatalogUnderData() {
    var response = new DataResponse<>(List.of());
    assertThat(JsonMapper.builder().build().writeValueAsString(response))
        .isEqualTo("{\"data\":[]}");
  }
}
