package com.github.theguuholi.rentcars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpecificationHttpTest {
  private static final String PATH = "/specifications";
  private static final String GPS =
      """
        {"name":"gps","description":"Navigation"}
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
            {"name":"navigation","description":"Satellite navigation"}
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("NAVIGATION"));
    String longDescription = "x".repeat(512);
    mvc.perform(
            patch(location)
                .contentType("application/json")
                .content(
                    """
            {"description":"%s"}
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
            {"name":"abs"}
            """))
        .andExpect(status().isConflict());
    mvc.perform(get(location)).andExpect(jsonPath("$.name").value("GPS"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"name\":\"GPS\"}", "{\"name\":\"  \",\"description\":null}"})
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
            {"description":null}
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

  @Autowired TransactionTemplate transactions;

  @Test
  void concurrentCreatesWithEquivalentNamesCommitExactlyOneRecord() throws Exception {
    var start = new CyclicBarrier(2);
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<Integer> create =
          () -> {
            start.await(10, TimeUnit.SECONDS);
            return mvc.perform(post(PATH).contentType("application/json").content(GPS))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = workers.submit(create);
      var second = workers.submit(create);
      assertThat(
              java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    mvc.perform(get(PATH))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("GPS"));
  }

  @Test
  void waitingPatchPreservesAnotherTransactionsRename() throws Exception {
    String location = createGps();
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      // Hold the first HTTP update open until PostgreSQL confirms that the second request is
      // blocked.
      var waiting =
          transactions.execute(
              status -> {
                try {
                  mvc.perform(
                          patch(location)
                              .contentType("application/json")
                              .content("{\"name\":\"sat nav\"}"))
                      .andExpect(status().isOk());
                  int owner = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
                  var next =
                      workers.submit(
                          () ->
                              mvc.perform(
                                      patch(location)
                                          .contentType("application/json")
                                          .content("{\"description\":\"Updated maps\"}"))
                                  .andReturn()
                                  .getResponse()
                                  .getStatus());
                  await()
                      .atMost(Duration.ofSeconds(10))
                      .until(
                          () ->
                              jdbc.queryForObject(
                                  "select exists (select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                                  Boolean.class,
                                  owner));
                  return next;
                } catch (Exception ex) {
                  throw new AssertionError(ex);
                }
              });
      assertThat(waiting.get(15, TimeUnit.SECONDS)).isEqualTo(200);
    }
    mvc.perform(get(location))
        .andExpect(jsonPath("$.name").value("SAT NAV"))
        .andExpect(jsonPath("$.description").value("Updated maps"));
  }

  @Test
  void invalidPatchRollsBackAllFieldsAndAllowsRetry() throws Exception {
    String location = createGps();
    mvc.perform(
            patch(location)
                .contentType("application/json")
                .content("{\"name\":\"sat nav\",\"description\":null}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.errors.description").isArray());
    mvc.perform(get(location))
        .andExpect(jsonPath("$.name").value("GPS"))
        .andExpect(jsonPath("$.description").value("Navigation"));
    mvc.perform(patch(location).contentType("application/json").content("{\"name\":\"sat nav\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("SAT NAV"));
  }

  @Test
  void listsInNameOrderAndAllowsReusingADeletedName() throws Exception {
    String original = createGps();
    mvc.perform(post(PATH).contentType("application/json").content(GPS.replace("gps", "abs")))
        .andExpect(status().isCreated());
    mvc.perform(get(PATH))
        .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("ABS", "GPS")));
    mvc.perform(delete(original)).andExpect(status().isNoContent()).andExpect(content().string(""));
    mvc.perform(delete(original)).andExpect(status().isNotFound());
    assertThat(createGps()).isNotEqualTo(original);
    mvc.perform(get(original)).andExpect(status().isNotFound());
  }

  @Test
  void emptyPatchPreservesTheRecordAndEmptyRequestBodyIsRejected() throws Exception {
    String location = createGps();
    mvc.perform(patch(location).contentType("application/json").content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("GPS"))
        .andExpect(jsonPath("$.description").value("Navigation"));
    mvc.perform(post(PATH).contentType("application/json")).andExpect(status().isBadRequest());
  }

  @Test
  void unknownSpecificationReturnsNotFoundForMutations() throws Exception {
    String missing = PATH + "/" + UUID.randomUUID();
    mvc.perform(put(missing).contentType("application/json").content(GPS))
        .andExpect(status().isNotFound());
    mvc.perform(delete(missing)).andExpect(status().isNotFound());
  }
}
