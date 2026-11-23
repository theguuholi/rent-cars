package com.github.theguuholi.recommendations.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(RecommendationHttpTest.Database.class)
@SpringBootTest(
    properties = {
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.listener.missing-topics-fatal=false",
      "spring.security.user.password=test-password"
    })
@AutoConfigureMockMvc
class RecommendationHttpTest {
  @TestConfiguration(proxyBeanMethods = false)
  static class Database {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
      return new PostgreSQLContainer("postgres:17");
    }
  }

  @Autowired MockMvc http;
  @Autowired JdbcTemplate database;

  @BeforeEach
  void clear() {
    database.update("DELETE FROM recommendation_snapshots");
  }

  @Test
  void givenHealthyServiceWhenProbeHasNoCredentialsThenReturnsUp() throws Exception {
    http.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void givenAuthenticatedClientWhenReadingOpenApiThenDocumentsRankingSchemaAndAuthentication()
      throws Exception {
    http.perform(get("/v3/api-docs").with(httpBasic("rent-cars", "test-password")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.components.schemas.RecommendedCar.properties.score.description")
                .value("Distinct rentals opened in the thirty-day window"))
        .andExpect(
            jsonPath("$.components.securitySchemes.serviceCredentials.scheme").value("basic"));
  }

  @Test
  void givenAuthenticatedClientWhenReadingUnexposedResourceThenReturnsForbidden() throws Exception {
    http.perform(get("/actuator/env").with(httpBasic("rent-cars", "test-password")))
        .andExpect(status().isForbidden());
  }

  @Nested
  class ListCars {
    @Test
    void givenNoCredentialsWhenReadingThenReturnsUnauthorized() throws Exception {
      http.perform(get("/recommendations/cars")).andExpect(status().isUnauthorized());
    }

    @Test
    void givenWrongCredentialsWhenReadingThenReturnsUnauthorized() throws Exception {
      http.perform(get("/recommendations/cars").with(httpBasic("rent-cars", "wrong")))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void givenNoCompletedBatchWhenReadingThenReturnsEmptyItems() throws Exception {
      http.perform(get("/recommendations/cars").with(httpBasic("rent-cars", "test-password")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.algorithm").value("popular-cars-v1"))
          .andExpect(jsonPath("$.generatedAt").isEmpty())
          .andExpect(jsonPath("$.asOf").isEmpty())
          .andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1", "wrong"})
    void givenInvalidLimitWhenReadingThenReturnsBadRequest(String limit) throws Exception {
      http.perform(
              get("/recommendations/cars")
                  .param("limit", limit)
                  .with(httpBasic("rent-cars", "test-password")))
          .andExpect(status().isBadRequest());
    }

    @Test
    void givenTwoSnapshotsWhenReadingThenReturnsNewestWithRequestedLimit() throws Exception {
      database.update(
          "INSERT INTO recommendation_snapshots VALUES ('2026-11-22T00:00:00Z','2026-11-23T00:00:00Z','popular-cars-v1','[]','old')");
      database.update(
          "INSERT INTO recommendation_snapshots VALUES ('2026-11-23T00:00:00Z','2026-11-23T00:00:00Z','popular-cars-v1', ?::jsonb,'new')",
          """
          [{"carId":"00000000-0000-0000-0000-000000000001","rank":1,"score":12},
           {"carId":"00000000-0000-0000-0000-000000000002","rank":2,"score":4}]
          """);
      http.perform(
              get("/recommendations/cars")
                  .param("limit", "1")
                  .with(httpBasic("rent-cars", "test-password")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.asOf").value("2026-11-23T00:00:00Z"))
          .andExpect(jsonPath("$.generatedAt").value("2026-11-23T00:00:00Z"))
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].score").value(12))
          .andExpect(jsonPath("$.items[0].rank").value(1))
          .andExpect(jsonPath("$.items[0].carId").value("00000000-0000-0000-0000-000000000001"));
      http.perform(get("/recommendations/cars").with(httpBasic("rent-cars", "test-password")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(2));
    }
  }
}
