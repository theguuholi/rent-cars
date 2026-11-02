package com.github.theguuholi.rentcars;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpecificationHttpTest {
  private static final String PATH = "/specifications";
  private static final String GPS =
      """
        {"specification":{"name":"gps","description":"Navigation"}}
        """;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void resetCatalog() {
    jdbc.update("DELETE FROM specifications");
  }

  private String createGps() throws Exception {
    return mvc.perform(post(PATH).contentType("application/json").content(GPS))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.name").value("GPS"))
        .andReturn()
        .getResponse()
        .getHeader("Location");
  }

  @Test
  void catalogLifecycle() throws Exception {
    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", org.springframework.test.json.JsonCompareMode.STRICT));
    String location = createGps();
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Navigation"));
    mvc.perform(get(PATH)).andExpect(jsonPath("$[0].name").value("GPS"));
    mvc.perform(
            put(location)
                .contentType("application/json")
                .content(
                    """
            {"specification":{"name":"navigation","description":"Satellite navigation"}}
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("NAVIGATION"));
    String longDescription = "x".repeat(512);
    mvc.perform(
            patch(location)
                .contentType("application/json")
                .content(
                    """
            {"specification":{"description":"%s"}}
            """
                        .formatted(longDescription)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("NAVIGATION"))
        .andExpect(jsonPath("$.description").value(longDescription));
    mvc.perform(delete(location)).andExpect(status().isNoContent());
    mvc.perform(get(location)).andExpect(status().isNotFound());
  }

  @Test
  void duplicateCreateAndRenameAreRejectedWithoutLosingData() throws Exception {
    String location = createGps();
    mvc.perform(post(PATH).contentType("application/json").content(GPS.replace("gps", "GpS")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors.conflict").isArray());
    mvc.perform(post(PATH).contentType("application/json").content(GPS.replace("gps", "abs")))
        .andExpect(status().isCreated());
    mvc.perform(
            patch(location)
                .contentType("application/json")
                .content(
                    """
            {"specification":{"name":"abs"}}
            """))
        .andExpect(status().isConflict());
    mvc.perform(get(location)).andExpect(jsonPath("$.name").value("GPS"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"specification\":{}}",
        "{\"specification\":{\"name\":\"  \",\"description\":null}}"
      })
  void rejectsMissingRequiredData(String json) throws Exception {
    mvc.perform(post(PATH).contentType("application/json").content(json))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.errors").isNotEmpty());
  }

  @Test
  void validatesExplicitNullAndNormalizedNameLength() throws Exception {
    String location = createGps();
    mvc.perform(
            patch(location)
                .contentType("application/json")
                .content(
                    """
            {"specification":{"description":null}}
            """))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.errors.description").isArray());
    mvc.perform(
            put(location)
                .contentType("application/json")
                .content(GPS.replace("gps", "x".repeat(256))))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.errors.name").isArray());
    mvc.perform(get(location)).andExpect(jsonPath("$.description").value("Navigation"));
  }

  @Test
  void malformedRequestsRetainMvcClientErrorStatuses() throws Exception {
    mvc.perform(get(PATH + "/invalid-uuid")).andExpect(status().isBadRequest());
    mvc.perform(post(PATH).contentType("application/json").content("{"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(PATH + "/" + UUID.randomUUID()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"));
  }

  @Test
  void unknownSpecificationReturnsNotFoundForMutations() throws Exception {
    String missing = PATH + "/" + UUID.randomUUID();
    mvc.perform(put(missing).contentType("application/json").content(GPS))
        .andExpect(status().isNotFound());
    mvc.perform(delete(missing)).andExpect(status().isNotFound());
  }
}
