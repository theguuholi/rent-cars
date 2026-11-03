package com.github.theguuholi.rentcars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
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

    @DisplayName("Given a non-object body, when creating, then the API rejects it before saving")
    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42"})
    void malformedBody(String body) throws Exception {
      mvc.perform(post("/cars").contentType("application/json").content(body))
          .andExpect(status().isBadRequest());
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
      create(input().put(field, "  ")).andExpect(status().is(422));
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
      create(input().put("categoryId", missing)).andExpect(status().isBadRequest());
      var badSpecs = input();
      badSpecs.putArray("specificationIds").add(gps).add(missing);
      create(badSpecs).andExpect(status().isBadRequest());
      mvc.perform(get("/cars")).andExpect(jsonPath("$").isEmpty());
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
        "Given an existing car, when fetching its location, then its representation matches the creation response")
    @Test
    void persistedCar() throws Exception {
      var created = create(input()).andExpect(status().isCreated()).andReturn().getResponse();
      mvc.perform(get(created.getHeader("Location")))
          .andExpect(status().isOk())
          .andExpect(content().json(created.getContentAsString()));
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
        "Given an assigned specification, when omitted it is preserved and when cleared it is removed")
    @Test
    void omittedAndEmptySpecifications() throws Exception {
      var car = input();
      car.putArray("specificationIds").add(gps);
      var created = create(car).andExpect(status().isCreated()).andReturn().getResponse();
      String location = created.getHeader("Location");
      edit(location, "{}")
          .andExpect(status().isOk())
          .andExpect(content().json(created.getContentAsString()));
      edit(location, "{\"specificationIds\":[]}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.specificationIds").isEmpty());
      mvc.perform(get(location)).andExpect(jsonPath("$.specificationIds").isEmpty());
    }

    @DisplayName("Given an explicit null, when patching, then the entire car stays unchanged")
    @ParameterizedTest
    @ValueSource(
        strings = {
          "name",
          "description",
          "brand",
          "licensePlate",
          "dailyRateCents",
          "fineAmountCents",
          "categoryId",
          "available",
          "specificationIds"
        })
    void givenExplicitNullWhenPatchingThenRejectsWithoutChangingTheCar(String field)
        throws Exception {
      String location = givenCar();
      String before = mvc.perform(get(location)).andReturn().getResponse().getContentAsString();
      edit(location, json.createObjectNode().put("name", "Mutated").putNull(field).toString())
          .andExpect(status().isBadRequest());
      mvc.perform(get(location)).andExpect(content().json(before));
    }

    @DisplayName("Given invalid cents, when patching, then the original amounts are preserved")
    @ParameterizedTest
    @ValueSource(strings = {"dailyRateCents", "fineAmountCents"})
    void givenNonIntegerOrNegativeCentsWhenPatchingThenPreservesOriginalAmount(String field)
        throws Exception {
      String location = givenCar();
      for (String value : List.of("0.5", "\"42\"", "2147483648", "true")) {
        edit(location, "{\"%s\":%s}".formatted(field, value)).andExpect(status().isBadRequest());
      }
      edit(location, "{\"%s\":-1}".formatted(field)).andExpect(status().is(422));
      mvc.perform(get(location))
          .andExpect(jsonPath("$.dailyRateCents").value(12345))
          .andExpect(jsonPath("$.fineAmountCents").value(0));
    }

    @DisplayName("Given a changed plate, when patching, then all edits roll back")
    @Test
    void givenExistingPlateWhenPatchingWithDifferentPlateThenRejectsTheWholePatch()
        throws Exception {
      String location = givenCar();
      edit(location, "{\"name\":\"New name\",\"licensePlate\":\"XYZ9876\"}")
          .andExpect(status().is(422));
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
      var badCategory = json.createObjectNode().put("categoryId", missing);
      var badSpecs = json.createObjectNode();
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
      var changes = json.createObjectNode().put("available", false).put(field, "x".repeat(max + 1));
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
      for (String status : List.of("400", "409", "422")) {
        docs.andExpect(jsonPath("$.paths['/cars'].post.responses['" + status + "']").exists());
      }
      for (String status : List.of("400", "404", "422")) {
        docs.andExpect(
            jsonPath("$.paths['/cars/{id}'].patch.responses['" + status + "']").exists());
      }
      docs.andExpect(
              jsonPath("$.paths['/cars'].post.responses['201'].content.*.schema['$ref']")
                  .value(org.hamcrest.Matchers.contains("#/components/schemas/Car")))
          .andExpect(
              jsonPath("$.paths['/cars'].get.responses['200'].content.*.schema.items['$ref']")
                  .value(org.hamcrest.Matchers.contains("#/components/schemas/Car")));
      String postBody = "$.paths['/cars'].post.requestBody.content['application/json'].schema";
      String patchBody =
          "$.paths['/cars/{id}'].patch.requestBody.content['application/json'].schema";
      docs.andExpect(jsonPath(postBody + "['$ref']").value("#/components/schemas/CarCreateRequest"))
          .andExpect(
              jsonPath("$.components.schemas.CarCreateRequest.required")
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
              jsonPath("$.components.schemas.CarCreateRequest.allOf[0]['$ref']")
                  .value("#/components/schemas/CarRequest"))
          .andExpect(jsonPath(patchBody + "['$ref']").value("#/components/schemas/CarRequest"))
          .andExpect(jsonPath("$.components.schemas.CarRequest.required").doesNotExist());
      docs.andExpect(jsonPath("$.components.schemas.Car.properties.id.readOnly").value(true));
      for (var schema : List.of("Car", "CarRequest")) {
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
            jsonPath("$.components.schemas.Car.properties." + field + ".format").value("uuid"));
      }
      for (String field : List.of("dailyRateCents", "fineAmountCents")) {
        docs.andExpect(
            jsonPath("$.components.schemas.Car.properties." + field + ".type").value("integer"));
      }
      docs.andExpect(
          jsonPath("$.components.schemas.Car.properties.specificationIds.items.format")
              .value("uuid"));
    }
  }
}
