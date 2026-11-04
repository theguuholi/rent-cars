package com.github.theguuholi.rentcars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  com.github.theguuholi.rentcars.auth.AdminRequests.class
})
class CarHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  private String sedan;
  private String suv;
  private String gps;
  private String abs;

  @BeforeEach
  void givenCatalogEntries() {
    sedan = category("SEDAN");
    suv = category("SUV");
    gps = specification("GPS");
    abs = specification("ABS");
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from cars");
    jdbc.update("delete from specifications");
  }

  private String category(String name) {
    return jdbc.queryForObject(
        "select id::text from categories where name = ?", String.class, name);
  }

  private String specification(String name) {
    return jdbc.queryForObject(
        "insert into specifications (name, description) values (?, ?) on conflict (name) do update set description = excluded.description returning id::text",
        String.class,
        name,
        name);
  }

  private ObjectNode input() {
    return json.createObjectNode()
        .put("name", "Civic")
        .put("description", "Family car")
        .put("brand", "Honda")
        .put("licensePlate", " abc1234 ")
        .put("dailyRateCents", 12345)
        .put("fineAmountCents", 0)
        .put("categoryId", sedan);
  }

  private ResultActions create(ObjectNode car) throws Exception {
    return mvc.perform(post("/cars").contentType("application/json").content(car.toString()));
  }

  private ResultActions edit(String location, String patchBody) throws Exception {
    return mvc.perform(patch(location).contentType("application/json").content(patchBody));
  }

  @Test
  @DisplayName(
      "Given two editors of the same rate, when both submit the same original value, then exactly one commits and the other receives 409")
  void concurrentRateChanges() throws Exception {
    String location = givenCar();
    var start = new CyclicBarrier(2);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<Integer> submit =
          () -> {
            start.await(10, TimeUnit.SECONDS);
            return edit(location, "{\"dailyRateCents\":20000,\"expectedVersion\":0}")
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = pool.submit(submit);
      var second = pool.submit(submit);
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    mvc.perform(get(location)).andExpect(jsonPath("$.dailyRateCents").value(20000));
  }

  @Test
  @DisplayName(
      "Given a changed equipment selection, when an older form replaces it, then all changes are rejected and reopening permits a fresh edit")
  void guardedEquipment() throws Exception {
    String location = givenCar();
    var patch = json.createObjectNode();
    patch.putArray("specificationIds").add(gps);
    patch.put("expectedVersion", 0);
    edit(location, patch.toString()).andExpect(status().isOk());
    patch.putArray("specificationIds").add(abs);
    patch.put("name", "Stale");
    edit(location, patch.toString()).andExpect(status().isConflict());
    edit(location, "{\"name\":\"Updated\",\"expectedVersion\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.specificationIds").value(containsInAnyOrder(gps)));
  }

  @Test
  @DisplayName(
      "Given a car, when a client edits without its version, then the API rejects the update")
  void missingEditVersion() throws Exception {
    edit(givenCar(), "{}").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Given a car with assigned equipment, when the specification is deleted, then only linked cars receive a new version")
  void cascadingUnassignmentChangesVersion() throws Exception {
    String unlinked = givenCar();
    var body = input().put("licensePlate", "REV1234");
    body.putArray("specificationIds").add(gps);
    String linked =
        create(body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    mvc.perform(delete("/specifications/" + gps)).andExpect(status().isNoContent());
    mvc.perform(get(linked))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.specificationIds").isEmpty());
    mvc.perform(get(unlinked)).andExpect(jsonPath("$.version").value(0));
    edit(linked, "{\"name\":\"Old edit\",\"expectedVersion\":0}").andExpect(status().isConflict());
  }

  @Autowired TransactionTemplate transactions;

  @Test
  @DisplayName(
      "Given a manual equipment edit in progress, when the development seed runs, then it waits and preserves the committed selection")
  void seedWaitsForManualSelection() throws Exception {
    specification("BLUETOOTH AUDIO");
    var car = input().put("name", "Argo").put("brand", "Fiat").put("licensePlate", "DEV1A01");
    car.putArray("specificationIds").add(gps);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var seeded =
          transactions.execute(
              status -> {
                try {
                  create(car).andExpect(status().isCreated());
                } catch (Exception failure) {
                  throw new AssertionError(failure);
                }
                int owner = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
                var worker =
                    pool.submit(
                        () ->
                            transactions.executeWithoutResult(
                                tx ->
                                    new ResourceDatabasePopulator(
                                            new ClassPathResource(
                                                "db/dev/R__seed_vehicle_specifications_for_cars.sql"))
                                        .execute(jdbc.getDataSource())));
                Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .until(
                        () ->
                            jdbc.queryForObject(
                                "select exists(select from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                                Boolean.class,
                                owner));
                return worker;
              });
      seeded.get(15, TimeUnit.SECONDS);
    }
    String location =
        "/cars/"
            + jdbc.queryForObject(
                "select id::text from cars where license_plate='DEV1A01'", String.class);
    mvc.perform(get(location))
        .andExpect(jsonPath("$.specificationIds").value(containsInAnyOrder(gps)));
    edit(location, "{\"expectedVersion\":0,\"specificationIds\":[]}").andExpect(status().isOk());
    new ResourceDatabasePopulator(
            new ClassPathResource("db/dev/R__seed_vehicle_specifications_for_cars.sql"))
        .execute(jdbc.getDataSource());
    mvc.perform(get(location)).andExpect(jsonPath("$.specificationIds").isEmpty());
  }

  // Use the same wire examples for POST and PATCH. A complete POST isolates each invalid field.
  private static final String INVALID_INPUTS =
      "com.github.theguuholi.rentcars.CarHttpTest#invalidInputs";

  static Stream<String> invalidInputs() {
    return """
        null
        []
        42
        {"extra":1}
        {"id":"12ac672c-89bd-4da2-bdb4-73f3733509ca"}
        {"insertedAt":"2025-01-01T00:00:00Z"}
        {"updatedAt":"2025-01-01T00:00:00Z"}
        {"name":42}
        {"brand":true}
        {"description":1.75}
        {"licensePlate":{}}
        {"categoryId":"invalid"}
        {"categoryId":4}
        {"specificationIds":{}}
        {"specificationIds":[null]}
        {"specificationIds":["invalid"]}
        {"available":"true"}
        {"available":1}
        {"dailyRateCents":0.5}
        {"dailyRateCents":"15050"}
        {"fineAmountCents":150.9}
        {"dailyRateCents":2147483648}
        {"fineAmountCents":"0"}
        {"fineAmountCents":false}
        {"name":null}
        {"description":null}
        {"brand":null}
        {"licensePlate":null}
        {"available":null}
        {"dailyRateCents":null}
        {"fineAmountCents":null}
        {"categoryId":null}
        {"specificationIds":null}
        """
        .lines();
  }

  private String invalidBody(ObjectNode validFields, String invalidInput) {
    var body = json.readTree(invalidInput);
    if (body instanceof ObjectNode fields) {
      validFields.setAll(fields);
      return validFields.toString();
    }
    return invalidInput;
  }

  private String givenCar() throws Exception {
    return create(input())
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");
  }

  @Nested
  @DisplayName("GET /cars")
  class ListCars {
    @DisplayName("Given an empty fleet, when listing cars, then the collection is empty")
    @Test
    void emptyFleet() throws Exception {
      mvc.perform(get("/cars")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @DisplayName(
        "Given two cars, when listing, then both appear in name order with their specifications")
    @Test
    void givenTwoCarsWhenListingThenReturnsBothInNameOrder() throws Exception {
      givenCar();
      var car =
          input().put("name", "Accord").put("licensePlate", "DEF5678").put("available", false);
      car.putArray("specificationIds").add(gps);
      create(car).andExpect(status().isCreated()).andExpect(jsonPath("$.available").value(false));
      mvc.perform(get("/cars"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("Accord", "Civic")))
          .andExpect(jsonPath("$[*].category").doesNotHaveJsonPath())
          .andExpect(jsonPath("$[*].specifications").doesNotHaveJsonPath())
          .andExpect(jsonPath("$[0].specificationIds").value(containsInAnyOrder(gps)));
    }
  }

  @Nested
  @DisplayName("POST /cars")
  class CreateCar {
    @DisplayName(
        "Given required attributes, when creating, then defaults, normalized plate and location are returned")
    @Test
    void createsCar() throws Exception {
      var created =
          create(input())
              .andExpect(status().isCreated())
              .andExpect(header().exists("Location"))
              .andExpect(jsonPath("$.available").value(true))
              .andExpect(jsonPath("$.specificationIds").isEmpty())
              .andExpect(jsonPath("$.licensePlate").value("ABC1234"))
              .andReturn()
              .getResponse();
      String location = created.getHeader("Location");
      String id = json.readTree(created.getContentAsString()).get("id").asString();
      assertThat(location).endsWith("/cars/" + UUID.fromString(id));
    }

    @DisplayName("Given malformed input, when creating a car, then it is rejected before saving")
    @ParameterizedTest
    @MethodSource(INVALID_INPUTS)
    void rejectsMalformedInput(String invalidInput) throws Exception {
      mvc.perform(
              post("/cars")
                  .contentType("application/json")
                  .content(invalidBody(input(), invalidInput)))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.detail").isString());
      mvc.perform(get("/cars")).andExpect(jsonPath("$").isEmpty());
    }

    @DisplayName("Given a missing required field, when creating, then the fleet stays empty")
    @ParameterizedTest
    @ValueSource(
        strings = {
          "name",
          "description",
          "brand",
          "licensePlate",
          "dailyRateCents",
          "fineAmountCents",
          "categoryId"
        })
    void givenMissingRequiredFieldWhenCreatingThenRejectsTheCar(String field) throws Exception {
      var car = input();
      car.remove(field);
      create(car).andExpect(status().is(422));
      mvc.perform(get("/cars")).andExpect(jsonPath("$").isEmpty());
    }

    @DisplayName("Given blank text, when creating, then validation rejects the car")
    @ParameterizedTest
    @ValueSource(strings = {"name", "description", "brand", "licensePlate"})
    void givenBlankTextWhenCreatingThenRejectsTheCar(String field) throws Exception {
      create(input().put(field, "  "))
          .andExpect(status().is(422))
          .andExpect(content().contentTypeCompatibleWith("application/json"))
          .andExpect(jsonPath("$.errors." + field + "[0]").value("must not be blank"));
    }

    @DisplayName(
        "Given bounded text, when creating, then the maximum length succeeds and one more character fails")
    @ParameterizedTest
    @CsvSource({"name,100", "brand,80", "licensePlate,20", "description,255"})
    void enforcesTextLength(String field, int max) throws Exception {
      create(input().put(field, "X".repeat(max + 1))).andExpect(status().is(422));
      mvc.perform(get("/cars")).andExpect(jsonPath("$").isEmpty());
      create(input().put(field, "X".repeat(max)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$." + field).value("X".repeat(max)));
    }

    @DisplayName("Given an unknown catalog reference, when creating, then no car is persisted")
    @Test
    void unknownReferences() throws Exception {
      String missing = UUID.randomUUID().toString();
      create(input().put("categoryId", missing))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith("application/json"))
          .andExpect(jsonPath("$.errors.reference[0]").value("A referenced record does not exist"));
      var badSpecs = input();
      badSpecs.putArray("specificationIds").add(gps).add(missing);
      create(badSpecs).andExpect(status().isBadRequest());
      mvc.perform(get("/cars")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName(
        "Given an existing plate, when registering it again, then the conflict has a JSON error message")
    void duplicatePlate() throws Exception {
      givenCar();
      create(input())
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith("application/json"))
          .andExpect(
              jsonPath("$.errors.conflict[0]")
                  .value("A record with the same unique value already exists"));
    }

    @DisplayName("Given equivalent plates, when two creates race, then exactly one commits")
    @Test
    void givenEquivalentPlatesWhenTwoCreatesRaceThenExactlyOneCarCommits() throws Exception {
      var start = new CyclicBarrier(2);
      try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
        var futures = new java.util.ArrayList<Future<Integer>>();
        for (String plate : List.of("XYZ1234", " xyz1234 ")) {
          futures.add(
              pool.submit(
                  () -> {
                    var payload = input().put("licensePlate", plate);
                    start.await(10, TimeUnit.SECONDS);
                    return create(payload).andReturn().getResponse().getStatus();
                  }));
        }
        assertThat(
                List.of(
                    futures.get(0).get(15, TimeUnit.SECONDS),
                    futures.get(1).get(15, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(201, 409);
      }
      mvc.perform(get("/cars"))
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].licensePlate").value("XYZ1234"));
    }
  }

  @Nested
  @DisplayName("GET /cars/{id}")
  class GetCar {
    @DisplayName(
        "Given an existing car, when fetching its location, then its detail retains the creation fields and shows the category with no assigned features")
    @Test
    void persistedCar() throws Exception {
      var created = create(input()).andExpect(status().isCreated()).andReturn().getResponse();
      var summary = json.readTree(created.getContentAsString());
      assertThat(summary.has("category")).isFalse();
      assertThat(summary.has("specifications")).isFalse();
      mvc.perform(get(created.getHeader("Location")))
          .andExpect(status().isOk())
          .andExpect(content().json(created.getContentAsString()))
          .andExpect(jsonPath("$.category.name").value("SEDAN"))
          .andExpect(jsonPath("$.specifications").isEmpty());
    }

    @Test
    @DisplayName(
        "Given a car whose category and equipment change, when fetching its detail, then only its current assignments and their latest names appear")
    void currentCatalogLabels() throws Exception {
      var car = input();
      car.putArray("specificationIds").add(gps);
      var location =
          create(car)
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getHeader("Location");
      var patch = json.createObjectNode().put("expectedVersion", 0).put("categoryId", suv);
      patch.putArray("specificationIds").add(abs);
      edit(location, patch.toString())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.category").doesNotHaveJsonPath())
          .andExpect(jsonPath("$.specifications").doesNotHaveJsonPath())
          .andExpect(jsonPath("$.categoryId").value(suv))
          .andExpect(jsonPath("$.specificationIds").value(containsInAnyOrder(abs)));
      jdbc.update("update specifications set name=? where id=?::uuid", "ANTI-LOCK BRAKES", abs);
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.category.name").value("SUV"))
          .andExpect(jsonPath("$.specificationIds").value(org.hamcrest.Matchers.contains(abs)))
          .andExpect(jsonPath("$.specifications[*].id").value(org.hamcrest.Matchers.contains(abs)))
          .andExpect(
              jsonPath("$.specifications[*].name")
                  .value(org.hamcrest.Matchers.contains("ANTI-LOCK BRAKES")));
    }

    @Test
    @DisplayName(
        "Given a category with no description, when fetching the car, then the public category includes its ID, name and null description")
    void nullableCategoryDescription() throws Exception {
      var id =
          jdbc.queryForObject(
              "insert into categories (name) values ('ROADSTER') returning id::text", String.class);
      try {
        var location =
            create(input().put("categoryId", id))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        mvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category.id").value(id))
            .andExpect(jsonPath("$.category.name").value("ROADSTER"))
            .andExpect(jsonPath("$.category.description").hasJsonPath())
            .andExpect(jsonPath("$.category.description").value(org.hamcrest.Matchers.nullValue()));
      } finally {
        jdbc.update("delete from categories where id=?::uuid", id);
      }
    }

    @DisplayName("Given an unknown car, when fetching it, then the API returns 404")
    @Test
    void missingCar() throws Exception {
      mvc.perform(get("/cars/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("PATCH /cars/{id}")
  class UpdateCar {
    @DisplayName(
        "Given a saved car, when patching attributes, then reads reflect the committed values")
    @Test
    void changesRequestedAttributes() throws Exception {
      String location = givenCar();
      String id = location.substring(location.lastIndexOf('/') + 1);
      var patch =
          input()
              .put("expectedVersion", 0)
              .put("name", "Corolla")
              .put("brand", "Toyota")
              .put("description", "Automatic")
              .put("dailyRateCents", 0)
              .put("fineAmountCents", 2147483647)
              .put("available", false)
              .put("categoryId", suv)
              .put("licensePlate", "abc1234");
      patch.putArray("specificationIds").add(abs).add(gps);
      edit(location, patch.toString()).andExpect(status().isOk());
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id))
          .andExpect(jsonPath("$.name").value("Corolla"))
          .andExpect(jsonPath("$.brand").value("Toyota"))
          .andExpect(jsonPath("$.description").value("Automatic"))
          .andExpect(jsonPath("$.licensePlate").value("ABC1234"))
          .andExpect(jsonPath("$.categoryId").value(suv))
          .andExpect(jsonPath("$.available").value(false))
          .andExpect(jsonPath("$.dailyRateCents").value(0))
          .andExpect(jsonPath("$.fineAmountCents").value(2147483647))
          .andExpect(jsonPath("$.specificationIds").value(containsInAnyOrder(abs, gps)));
    }

    @DisplayName(
        "Given an assigned specification, when a patch omits the selection or sends an empty list, then the link is preserved or removed respectively")
    @Test
    void omittedAndEmptySpecifications() throws Exception {
      var car = input().put("available", false);
      car.putArray("specificationIds").add(gps).add(gps);
      var created =
          create(car)
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.specificationIds").value(containsInAnyOrder(gps)))
              .andReturn()
              .getResponse();
      String location = created.getHeader("Location");
      edit(location, "{\"expectedVersion\":0}")
          .andExpect(status().isOk())
          .andExpect(content().json(created.getContentAsString()));
      edit(location, "{\"expectedVersion\":0,\"specificationIds\":[]}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.specificationIds").isEmpty());
      mvc.perform(get(location)).andExpect(jsonPath("$.specificationIds").isEmpty());
    }

    @DisplayName("Given malformed input, when patching, then the entire car stays unchanged")
    @ParameterizedTest
    @MethodSource(INVALID_INPUTS)
    void rejectsMalformedInput(String invalidInput) throws Exception {
      var created = create(input()).andExpect(status().isCreated()).andReturn().getResponse();
      String location = created.getHeader("Location");
      edit(location, invalidBody(json.createObjectNode().put("brand", "Changed"), invalidInput))
          .andExpect(status().isBadRequest());
      mvc.perform(get(location)).andExpect(content().json(created.getContentAsString()));
    }

    @Test
    @DisplayName(
        "Given vendor JSON, when patching a price, then integers are accepted and fractional centavos are rejected")
    void appliesTheSameContractToVendorJson() throws Exception {
      var location = givenCar();
      var mediaType = "application/vnd.rent+json";
      mvc.perform(
              patch(location)
                  .contentType(mediaType)
                  .content("{\"expectedVersion\":0,\"dailyRateCents\":15050}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dailyRateCents").value(15050));
      mvc.perform(patch(location).contentType(mediaType).content("{\"dailyRateCents\":150.9}"))
          .andExpect(status().isBadRequest());
      mvc.perform(get(location)).andExpect(jsonPath("$.dailyRateCents").value(15050));
    }

    @DisplayName("Given negative cents, when patching, then the original amounts are preserved")
    @ParameterizedTest
    @ValueSource(strings = {"dailyRateCents", "fineAmountCents"})
    void rejectsNegativeCents(String field) throws Exception {
      String location = givenCar();
      edit(location, json.createObjectNode().put("expectedVersion", 0).put(field, -1).toString())
          .andExpect(status().is(422));
      mvc.perform(get(location))
          .andExpect(jsonPath("$.dailyRateCents").value(12345))
          .andExpect(jsonPath("$.fineAmountCents").value(0));
    }

    @DisplayName("Given a changed plate, when patching, then all edits roll back")
    @Test
    void givenExistingPlateWhenPatchingWithDifferentPlateThenRejectsTheWholePatch()
        throws Exception {
      String location = givenCar();
      edit(location, "{\"expectedVersion\":0,\"name\":\"New name\",\"licensePlate\":\"XYZ9876\"}")
          .andExpect(status().is(422))
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.detail").value("License plate cannot be changed"));
      mvc.perform(get(location))
          .andExpect(jsonPath("$.name").value("Civic"))
          .andExpect(jsonPath("$.licensePlate").value("ABC1234"));
    }

    @DisplayName(
        "Given an unknown replacement reference, when patching, then all attributes and links are preserved")
    @Test
    void unknownReference() throws Exception {
      var car = input();
      car.putArray("specificationIds").add(abs);
      var created = create(car).andExpect(status().isCreated()).andReturn().getResponse();
      String location = created.getHeader("Location");
      String missing = UUID.randomUUID().toString();
      var badCategory =
          json.createObjectNode().put("expectedVersion", 0).put("categoryId", missing);
      var badSpecs = json.createObjectNode().put("expectedVersion", 0);
      badSpecs.putArray("specificationIds").add(gps).add(missing);
      for (var changes : List.of(badCategory, badSpecs)) {
        edit(location, changes.put("name", "Changed").toString())
            .andExpect(status().isBadRequest());
        mvc.perform(get(location)).andExpect(content().json(created.getContentAsString()));
      }
    }

    @ParameterizedTest
    @CsvSource({"name,100", "brand,80", "description,255"})
    @DisplayName(
        "Given an overlong attribute and valid edits, when patching, then all changes roll back")
    void overlongText(String field, int max) throws Exception {
      String location = givenCar();
      String before = mvc.perform(get(location)).andReturn().getResponse().getContentAsString();
      var changes =
          json.createObjectNode()
              .put("expectedVersion", 0)
              .put("available", false)
              .put(field, "x".repeat(max + 1));
      changes.putArray("specificationIds").add(gps);
      edit(location, changes.toString()).andExpect(status().is(422));
      mvc.perform(get(location)).andExpect(content().json(before));
    }

    @DisplayName("Given an unknown car, when patching, then the API returns 404")
    @Test
    void missingCar() throws Exception {
      edit("/cars/" + UUID.randomUUID(), "{}").andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("DELETE /cars/{id}")
  class DeleteCar {
    @DisplayName("Given an existing car, when deleting it, then its plate can be registered again")
    @Test
    void removesCar() throws Exception {
      String location = givenCar();
      mvc.perform(delete(location))
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));
      mvc.perform(get(location)).andExpect(status().isNotFound());
      assertThat(givenCar()).isNotEqualTo(location);
    }

    @DisplayName("Given an unknown car, when deleting, then the API returns 404")
    @Test
    void missingCar() throws Exception {
      mvc.perform(delete("/cars/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("GET /v3/api-docs")
  class ApiDocumentation {
    @Test
    @DisplayName(
        "Given a persisted car, when comparing its JSON and schema, then every public field has documentation and audit timestamps stay internal")
    void documentsPublicFieldsOfTheCarResponse() throws Exception {
      var response =
          mvc.perform(get(givenCar())).andExpect(status().isOk()).andReturn().getResponse();
      var car = json.readTree(response.getContentAsString());
      var docs =
          mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse();
      var schema = json.readTree(docs.getContentAsString()).at("/components/schemas/Car_Detail");
      var properties = schema.get("properties");

      assertThat(car.propertyNames())
          .containsExactlyInAnyOrder(
              "id",
              "name",
              "description",
              "brand",
              "licensePlate",
              "available",
              "dailyRateCents",
              "fineAmountCents",
              "categoryId",
              "version",
              "specificationIds",
              "category",
              "specifications");
      assertThat(car.get("dailyRateCents").isIntegralNumber()).isTrue();
      assertThat(car.get("dailyRateCents").asInt()).isEqualTo(12345);
      assertThat(car.get("fineAmountCents").isIntegralNumber()).isTrue();
      assertThat(car.get("fineAmountCents").asInt()).isZero();
      assertThat(schema.get("description").asString()).contains("Brazilian centavos");
      assertThat(properties.propertyNames())
          .containsExactlyInAnyOrderElementsOf(car.propertyNames());
      for (var field : car.propertyNames()) {
        assertThat(properties.get(field).path("description").asString("")).as(field).isNotBlank();
      }
      for (var charge : List.of("dailyRateCents", "fineAmountCents")) {
        assertThat(properties.get(charge).get("minimum").asInt()).isZero();
        assertThat(properties.get(charge).has("example")).isTrue();
      }
      assertThat(properties.get("licensePlate").get("example").asString()).isEqualTo("ABC1D23");
    }

    @DisplayName(
        "Given the fleet API, when reading OpenAPI, then the operations and car schemas are published")
    @Test
    void givenDocumentedFleetWhenReadingOpenApiThenPublishesEachOperationAndTheCarSchema()
        throws Exception {
      var docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
      for (var op : Map.of("get", "200", "post", "201").entrySet()) {
        docs.andExpect(
            jsonPath("$.paths['/cars']." + op.getKey() + ".responses['" + op.getValue() + "']")
                .exists());
      }
      for (var op : Map.of("get", "200", "patch", "200", "delete", "204").entrySet()) {
        docs.andExpect(
            jsonPath("$.paths['/cars/{id}']." + op.getKey() + ".responses['" + op.getValue() + "']")
                .exists());
      }
      for (String operation : List.of("['/cars'].post", "['/cars/{id}'].patch")) {
        String responses = "$.paths" + operation + ".responses";
        for (String code : List.of("400", "422", "500")) {
          docs.andExpect(
              jsonPath(responses + "['" + code + "'].content['application/json'].schema['$ref']")
                  .value("#/components/schemas/Errors_Summary"));
        }
        docs.andExpect(
            jsonPath(responses + "['400'].content['application/problem+json'].schema['$ref']")
                .value("#/components/schemas/ProblemDetail_Summary"));
      }
      docs.andExpect(
          jsonPath(
                  "$.paths['/cars'].post.responses['409'].content['application/json'].schema['$ref']")
              .value("#/components/schemas/Errors_Summary"));
      docs.andExpect(
          jsonPath(
                  "$.paths['/cars/{id}'].patch.responses['422'].content['application/problem+json'].schema['$ref']")
              .value("#/components/schemas/ProblemDetail_Summary"));
      for (var responseSchema :
          Map.of(
                  "get",
                  "ProblemDetail_Detail",
                  "patch",
                  "ProblemDetail_Summary",
                  "delete",
                  "ProblemDetail")
              .entrySet()) {
        docs.andExpect(
            jsonPath(
                    "$.paths['/cars/{id}']."
                        + responseSchema.getKey()
                        + ".responses['404'].content['application/problem+json'].schema['$ref']")
                .value("#/components/schemas/" + responseSchema.getValue()));
      }
      docs.andExpect(
              jsonPath("$.paths['/cars'].post.responses['201'].content.*.schema['$ref']")
                  .value(org.hamcrest.Matchers.contains("#/components/schemas/Car_Summary")))
          .andExpect(
              jsonPath("$.paths['/cars'].get.responses['200'].content.*.schema.items['$ref']")
                  .value(org.hamcrest.Matchers.contains("#/components/schemas/Car_Summary")));
      docs.andExpect(
          jsonPath("$.paths['/cars/{id}'].get.responses['200'].content.*.schema['$ref']")
              .value(org.hamcrest.Matchers.contains("#/components/schemas/Car_Detail")));
      String postBody = "$.paths['/cars'].post.requestBody.content['application/json'].schema";
      String patchBody =
          "$.paths['/cars/{id}'].patch.requestBody.content['application/json'].schema";
      docs.andExpect(
              jsonPath(postBody + "['$ref']")
                  .value("#/components/schemas/CarCreateRequest_Summary"))
          .andExpect(
              jsonPath("$.components.schemas.CarCreateRequest_Summary.required")
                  .value(
                      containsInAnyOrder(
                          "name",
                          "description",
                          "brand",
                          "licensePlate",
                          "dailyRateCents",
                          "fineAmountCents",
                          "categoryId")))
          .andExpect(
              jsonPath("$.components.schemas.CarCreateRequest_Summary.allOf[0]['$ref']")
                  .value("#/components/schemas/CarRequest_Summary"))
          .andExpect(
              jsonPath(patchBody + "['$ref']")
                  .value("#/components/schemas/CarUpdateRequest_Summary"))
          .andExpect(
              jsonPath("$.components.schemas.CarUpdateRequest_Summary.required")
                  .value(org.hamcrest.Matchers.contains("expectedVersion")));
      docs.andExpect(
          jsonPath("$.components.schemas.Car_Summary.properties.id.readOnly").value(true));
      for (var schema : List.of("Car_Summary", "CarRequest_Summary")) {
        String properties = "$.components.schemas." + schema + ".properties.";
        for (var bound :
            Map.of("name", 100, "brand", 80, "licensePlate", 20, "description", 255).entrySet()) {
          docs.andExpect(
                  jsonPath(properties + bound.getKey() + ".maxLength").value(bound.getValue()))
              .andExpect(jsonPath(properties + bound.getKey() + ".description").isNotEmpty());
        }
      }
      for (String field : List.of("id", "categoryId")) {
        docs.andExpect(
            jsonPath("$.components.schemas.Car_Summary.properties." + field + ".format")
                .value("uuid"));
      }
      for (String field : List.of("dailyRateCents", "fineAmountCents")) {
        docs.andExpect(
            jsonPath("$.components.schemas.Car_Summary.properties." + field + ".type")
                .value("integer"));
      }
      docs.andExpect(
          jsonPath("$.components.schemas.Car_Summary.properties.specificationIds.items.format")
              .value("uuid"));
    }
  }
}
