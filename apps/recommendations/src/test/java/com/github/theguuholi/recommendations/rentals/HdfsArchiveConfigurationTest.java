package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import tools.jackson.databind.json.JsonMapper;

class HdfsArchiveConfigurationTest {
  @Nested
  class RentalEventArchiveBean {
    @Test
    void givenEnabledArchiveWhenBuildingClientThenBoundsConnectAndReadTimeouts() {
      try (var factories = mockConstruction(SimpleClientHttpRequestFactory.class)) {
        var archive =
            new HdfsArchiveConfiguration()
                .rentalEventArchive(
                    JsonMapper.builder().build(), "http://localhost:14000", "hadoop", "/events");
        assertThat(archive).isNotNull();
        assertThat(factories.constructed()).hasSize(1);
        verify(factories.constructed().getFirst()).setConnectTimeout(3000);
        verify(factories.constructed().getFirst()).setReadTimeout(5000);
      }
    }
  }
}
