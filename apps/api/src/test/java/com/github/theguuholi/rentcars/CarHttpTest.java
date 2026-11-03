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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

  @Test
  void givenEmptyFleetWhenManagingACarThenEachReadReflectsTheCommittedChange() throws Exception {
    mvc.perform(get("/cars")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
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
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(content().json(created.getContentAsString()));
    mvc.perform(get("/cars")).andExpect(jsonPath("$[0].id").value(id));
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
    var updated =
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
            .andExpect(jsonPath("$.specificationIds").value(containsInAnyOrder(abs, gps)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    edit(location, "{}").andExpect(status().isOk()).andExpect(content().json(updated));
    edit(location, "{\"specificationIds\":[]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.specificationIds").isEmpty());
    mvc.perform(delete(location)).andExpect(status().isNoContent()).andExpect(content().string(""));
    mvc.perform(get(location)).andExpect(status().isNotFound());
    edit(location, "{}").andExpect(status().isNotFound());
    mvc.perform(delete(location)).andExpect(status().isNotFound());
    assertThat(givenCar()).isNotEqualTo(location);
  }

  @Test
  void givenTwoCarsWhenListingThenReturnsBothInNameOrder() throws Exception {
    givenCar();
    var car = input().put("name", "Accord").put("licensePlate", "DEF5678").put("available", false);
    car.putArray("specificationIds").add(gps);
    create(car).andExpect(status().isCreated()).andExpect(jsonPath("$.available").value(false));
    mvc.perform(get("/cars"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("Accord", "Civic")))
        .andExpect(jsonPath("$[0].specificationIds").value(containsInAnyOrder(gps)));
  }

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

  @ParameterizedTest
  @ValueSource(strings = {"name", "description", "brand", "licensePlate"})
  void givenBlankTextWhenCreatingThenRejectsTheCar(String field) throws Exception {
    create(input().put(field, "  ")).andExpect(status().is(422));
  }

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

  @Test
  void givenExistingPlateWhenPatchingWithDifferentPlateThenRejectsTheWholePatch() throws Exception {
    String location = givenCar();
    edit(location, "{\"name\":\"New name\",\"licensePlate\":\"XYZ9876\"}")
        .andExpect(status().is(422));
    mvc.perform(get(location))
        .andExpect(jsonPath("$.name").value("Civic"))
        .andExpect(jsonPath("$.licensePlate").value("ABC1234"));
  }

  @Test
  void givenUnknownReferencesWhenWritingThenRejectsAndRollsBack() throws Exception {
    String missing = UUID.randomUUID().toString();
    var badCategory = input().put("categoryId", missing);
    create(badCategory).andExpect(status().isBadRequest());
    var badSpecs = input();
    badSpecs.putArray("specificationIds").add(gps).add(missing);
    create(badSpecs).andExpect(status().isBadRequest());
    String location = givenCar();
    String before = mvc.perform(get(location)).andReturn().getResponse().getContentAsString();
    for (var invalid : List.of(badCategory, badSpecs)) {
      edit(location, invalid.put("name", "Invalid").toString()).andExpect(status().isBadRequest());
      mvc.perform(get(location)).andExpect(content().json(before));
    }
  }

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
    String postBody = "$.paths['/cars'].post.requestBody.content['application/json'].schema";
    String patchBody = "$.paths['/cars/{id}'].patch.requestBody.content['application/json'].schema";
    docs.andExpect(
            jsonPath(postBody + ".required")
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
            jsonPath(postBody + ".allOf[0]['$ref']").value("#/components/schemas/CarRequest"))
        .andExpect(jsonPath(patchBody + "['$ref']").value("#/components/schemas/CarRequest"))
        .andExpect(jsonPath("$.components.schemas.CarRequest.required").doesNotExist());
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
