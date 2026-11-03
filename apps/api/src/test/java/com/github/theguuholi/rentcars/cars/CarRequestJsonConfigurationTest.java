package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.theguuholi.rentcars.specifications.SpecificationRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import tools.jackson.databind.json.JsonMapper;

class CarRequestJsonConfigurationTest {
  @Nested
  @DisplayName("configureMessageConverters")
  class ConfigureMessageConverters {
    @Test
    @DisplayName(
        "Given a shared JSON converter, when binding Cars and Specifications, then only Cars rejects an explicitly null field during decoding")
    void scopesNullHandlingToCarRequests() throws Exception {
      var mapper = JsonMapper.builder().build();
      var json = new JacksonJsonHttpMessageConverter(mapper);
      var converters = HttpMessageConverters.forServer().registerDefaults().withJsonConverter(json);
      new CarRequestJsonConfiguration().configureMessageConverters(converters);
      converters.build();

      var carInput = nullName();
      assertThatThrownBy(() -> json.read(CarRequest.class, carInput))
          .isInstanceOf(HttpMessageNotReadableException.class);
      var specification = json.read(SpecificationRequest.class, nullName());
      assertThat(mapper.valueToTree(specification).get("name").asString()).isEmpty();
    }

    private MockHttpInputMessage nullName() {
      var input = new MockHttpInputMessage("{\"name\":null}".getBytes(StandardCharsets.UTF_8));
      input.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      return input;
    }
  }
}
