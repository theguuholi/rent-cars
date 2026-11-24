package com.github.theguuholi.rentcars.cars;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CarRecommendationsControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean PopularCarsClient rankings;

  @Nested
  class ListRecommendations {
    @Test
    @Transactional
    @DisplayName(
        "Given an anonymous visitor and a ranking, when requesting recommendations, then current available cars use the public summary schema")
    void publicRanking() throws Exception {
      var id =
          jdbc.queryForObject(
              """
              insert into cars (name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
              select 'Popular','Family','Honda','POPULAR1',id,15050,500 from categories where name='SEDAN' returning id
              """,
              UUID.class);
      when(rankings.carIds()).thenReturn(List.of(id));
      mvc.perform(get("/cars/recommendations"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].id").value(id.toString()))
          .andExpect(jsonPath("$[0].name").value("Popular"))
          .andExpect(jsonPath("$[0].available").value(true))
          .andExpect(jsonPath("$[0].dailyRateCents").value(15050))
          .andExpect(jsonPath("$[0].categoryId").isString())
          .andExpect(jsonPath("$[0].specificationIds").isArray())
          .andExpect(jsonPath("$[0].category").doesNotExist())
          .andExpect(jsonPath("$[0].specifications").doesNotExist());
      jdbc.update("update cars set available=false where id=?", id);
      mvc.perform(get("/cars/recommendations"))
          .andExpect(status().isOk())
          .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName(
        "Given no usable ranking, when an anonymous visitor requests recommendations, then the documented response is an empty array")
    void empty() throws Exception {
      when(rankings.carIds()).thenReturn(List.of());
      mvc.perform(get("/cars/recommendations"))
          .andExpect(status().isOk())
          .andExpect(content().json("[]"));
      mvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath(
                      "$.paths['/cars/recommendations'].get.responses['200'].content['application/json'].schema.type")
                  .value("array"));
      mvc.perform(get("/cars/manage")).andExpect(status().isUnauthorized());
      mvc.perform(get("/categories")).andExpect(status().isUnauthorized());
    }
  }
}
