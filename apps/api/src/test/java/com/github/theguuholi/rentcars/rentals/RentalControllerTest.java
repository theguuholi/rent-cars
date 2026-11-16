package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

class RentalControllerTest extends RentalIntegrationSupport {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JwtEncoder encoder;

  String token(UUID account, String role) {
    var now = Instant.now();
    return "Bearer "
        + encoder
            .encode(
                JwtEncoderParameters.from(
                    JwtClaimsSet.builder()
                        .issuer("rent-cars")
                        .audience(List.of("rent-cars-api"))
                        .subject(account.toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim("roles", List.of(role))
                        .build()))
            .getTokenValue();
  }

  ResultActions create(UUID owner, UUID carId) throws Exception {
    return mvc.perform(
        post("/rentals")
            .header("Authorization", token(owner, "USER"))
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of(
                        "carId",
                        carId,
                        "expectedReturnDate",
                        clock.instant().plusSeconds(86400).toString()))));
  }

  UUID rent() throws Exception {
    var response = create(owner, carId).andExpect(status().isCreated()).andReturn().getResponse();
    return UUID.fromString(json.readTree(response.getContentAsString()).get("id").asString());
  }

  ResultActions giveBack(UUID id, UUID owner) throws Exception {
    return mvc.perform(
        post("/rentals/{id}/return", id).header("Authorization", token(owner, "USER")));
  }

  @Nested
  class Create {
    @Test
    void givenDriverAndAvailableCarWhenCreatingThenReturnsDocumentedRentalAndLocation()
        throws Exception {
      var response =
          create(owner, carId)
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.accountId").value(owner.toString()))
              .andExpect(jsonPath("$.car.id").value(carId.toString()))
              .andExpect(jsonPath("$.car.available").value(false))
              .andExpect(jsonPath("$.car.version").value(1))
              .andExpect(jsonPath("$.startDate").value(NOW.toString()))
              .andExpect(jsonPath("$.expectedReturnDate").value(NOW.plusSeconds(86400).toString()))
              .andExpect(jsonPath("$.totalCents").value(org.hamcrest.Matchers.nullValue()))
              .andExpect(jsonPath("$.endDate").value(org.hamcrest.Matchers.nullValue()))
              .andExpect(jsonPath("$.insertedAt").doesNotExist())
              .andReturn()
              .getResponse();
      var rental = json.readTree(response.getContentAsString());
      assertThat(response.getHeader("Location"))
          .isEqualTo("/rentals/" + rental.get("id").asString());
      assertThat(available(carId)).isFalse();
      var schema =
          json.readTree(
              mvc.perform(get("/v3/api-docs"))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      var props = schema.at("/components/schemas/Rental/properties");
      for (String field :
          List.of(
              "id",
              "accountId",
              "carId",
              "car",
              "startDate",
              "expectedReturnDate",
              "endDate",
              "totalCents",
              "dailyRateCents",
              "fineAmountCents",
              "billedDays",
              "lateDays")) {
        assertThat(props.has(field)).as(field).isTrue();
        assertThat(rental.has(field)).as(field).isTrue();
      }
      assertThat(props.get("id").get("format").asString()).isEqualTo("uuid");
      assertThat(props.get("startDate").get("format").asString()).isEqualTo("date-time");
      assertThat(props.get("totalCents").get("format").asString()).isEqualTo("int64");
      assertThat(schema.at("/paths/~1rentals/post/responses/201").isMissingNode()).isFalse();
    }

    @Test
    void givenExistingRentalWhenCreatingAnotherThenReturnsConflict() throws Exception {
      rent();
      create(owner, car()).andExpect(status().isConflict());
    }

    @Test
    void givenUnavailableCarWhenAnotherDriverRentsThenReturnsConflict() throws Exception {
      rent();
      create(other, carId)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.detail").value("Car is unavailable"));
    }

    @Test
    void givenUnknownCarWhenCreatingThenReturnsNotFound() throws Exception {
      create(owner, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"carId\":null,\"expectedReturnDate\":null}"})
    void givenMissingFieldsWhenCreatingThenReturnsValidationErrors(String body) throws Exception {
      mvc.perform(
              post("/rentals")
                  .header("Authorization", token(owner, "USER"))
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.errors.carId").exists())
          .andExpect(jsonPath("$.errors.expectedReturnDate").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-07T14:59:59Z", "2026-11-05T15:00:00Z"})
    void givenShortOrPastPeriodWhenCreatingThenKeepsCarAvailable(String date) throws Exception {
      mvc.perform(
              post("/rentals")
                  .header("Authorization", token(owner, "USER"))
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(Map.of("carId", carId, "expectedReturnDate", date))))
          .andExpect(status().isUnprocessableContent());
      assertThat(available(carId)).isTrue();
    }

    @Test
    void givenMalformedDateWhenCreatingThenReturnsBadRequest() throws Exception {
      mvc.perform(
              post("/rentals")
                  .header("Authorization", token(owner, "USER"))
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          Map.of("carId", carId, "expectedReturnDate", "tomorrow"))))
          .andExpect(status().isBadRequest());
    }

    @Test
    void givenOffsetDateWhenCreatingThenNormalizesToUtc() throws Exception {
      mvc.perform(
              post("/rentals")
                  .header("Authorization", token(owner, "USER"))
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          Map.of(
                              "carId",
                              carId,
                              "expectedReturnDate",
                              "2026-11-07T12:00:00-03:00",
                              "accountId",
                              other))))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.accountId").value(owner.toString()))
          .andExpect(jsonPath("$.expectedReturnDate").value("2026-11-07T15:00:00Z"));
    }

    @Test
    void givenNoTokenWhenCreatingThenReturnsUnauthorized() throws Exception {
      mvc.perform(post("/rentals").contentType("application/json").content("{}"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class ListRentals {
    @Test
    void givenNoRentalsWhenListingThenReturnsEmptyArray() throws Exception {
      mvc.perform(get("/rentals").header("Authorization", token(owner, "USER")))
          .andExpect(status().isOk())
          .andExpect(content().json("[]"));
    }

    @Test
    void givenTwoOwnersWhenListingThenReturnsOnlyOwnHistoryNewestFirst() throws Exception {
      UUID first = rent();
      giveBack(first, owner).andExpect(status().isOk());
      when(clock.instant()).thenReturn(NOW.plusSeconds(1));
      UUID second = rent();
      create(other, car()).andExpect(status().isCreated());
      mvc.perform(get("/rentals").header("Authorization", token(owner, "USER")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].id").value(second.toString()))
          .andExpect(jsonPath("$[1].id").value(first.toString()))
          .andExpect(jsonPath("$[0].car.name").value("Rental test"));
    }

    @Test
    void givenNoTokenWhenListingThenReturnsUnauthorized() throws Exception {
      mvc.perform(get("/rentals")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class ReturnCar {
    @Test
    void givenOpenRentalWhenReturningThenChargesAndReleasesCar() throws Exception {
      UUID id = rent();
      when(clock.instant()).thenReturn(NOW.plusSeconds(259200));
      giveBack(id, owner)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalCents").value(36000))
          .andExpect(jsonPath("$.dailyRateCents").value(10000))
          .andExpect(jsonPath("$.fineAmountCents").value(3000))
          .andExpect(jsonPath("$.billedDays").value(3))
          .andExpect(jsonPath("$.lateDays").value(2))
          .andExpect(jsonPath("$.endDate").value(NOW.plusSeconds(259200).toString()))
          .andExpect(jsonPath("$.car.available").value(true))
          .andExpect(jsonPath("$.car.version").value(2));
      assertThat(available(carId)).isTrue();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
      "0,10000,1,0",
      "86399,10000,1,0",
      "86400,10000,1,0",
      "172800,23000,2,1"
    })
    void givenElapsedDaysWhenReturningThenChargesCompletedDaysAndLateFine(
        long seconds, long total, long days, long lateDays) throws Exception {
      UUID id = rent();
      when(clock.instant()).thenReturn(NOW.plusSeconds(seconds));
      giveBack(id, owner)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalCents").value(total))
          .andExpect(jsonPath("$.billedDays").value(days))
          .andExpect(jsonPath("$.lateDays").value(lateDays));
    }

    @Test
    void givenReturnedCarWhenRentingAgainThenTheSameDriverCanOpenAnotherRental() throws Exception {
      UUID first = rent();
      giveBack(first, owner).andExpect(status().isOk());
      UUID second = rent();
      assertThat(second).isNotEqualTo(first);
      assertThat(available(carId)).isFalse();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from rentals where car_id=? and end_date is null",
                  Integer.class,
                  carId))
          .isEqualTo(1);
    }

    @Test
    void givenOtherOwnerOrMissingRentalWhenReturningThenReturnsNotFound() throws Exception {
      UUID id = rent();
      giveBack(id, other).andExpect(status().isNotFound());
      giveBack(UUID.randomUUID(), owner).andExpect(status().isNotFound());
      assertThat(available(carId)).isFalse();
    }

    @Test
    void givenOldReturnedRentalAndNewRenterWhenReturningAgainThenDoesNotReleaseCar()
        throws Exception {
      UUID id = rent();
      giveBack(id, owner).andExpect(status().isOk());
      create(other, carId).andExpect(status().isCreated());
      giveBack(id, owner).andExpect(status().isConflict());
      assertThat(available(carId)).isFalse();
    }

    @Test
    void givenClockBeforeStartWhenReturningThenKeepsRentalOpen() throws Exception {
      UUID id = rent();
      when(clock.instant()).thenReturn(NOW.minusSeconds(1));
      giveBack(id, owner).andExpect(status().isUnprocessableContent());
      assertThat(available(carId)).isFalse();
      assertThat(
              jdbc.queryForObject(
                  "select end_date is null from rentals where id=?", Boolean.class, id))
          .isTrue();
    }

    @Test
    void givenNoTokenWhenReturningThenReturnsUnauthorized() throws Exception {
      mvc.perform(post("/rentals/{id}/return", UUID.randomUUID()))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class CatalogIntegration {
    private String adminToken() {
      jdbc.update("update accounts set role='ADMIN' where id=?", owner);
      return token(owner, "ADMIN");
    }

    @Test
    void givenActiveRentalWhenAdminMakesAvailableThenRejectsEvenWithCurrentVersion()
        throws Exception {
      rent();
      mvc.perform(
              patch("/cars/{id}", carId)
                  .header("Authorization", adminToken())
                  .contentType("application/json")
                  .content("{\"available\":true,\"expectedVersion\":1}"))
          .andExpect(status().isConflict());
      assertThat(available(carId)).isFalse();
      mvc.perform(
              patch("/cars/{id}", carId)
                  .header("Authorization", adminToken())
                  .contentType("application/json")
                  .content("{\"dailyRateCents\":12000,\"expectedVersion\":1}"))
          .andExpect(status().isOk());
      UUID id = jdbc.queryForObject("select id from rentals where car_id=?", UUID.class, carId);
      giveBack(id, owner)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalCents").value(10000))
          .andExpect(jsonPath("$.dailyRateCents").value(10000));
    }

    @Test
    void givenClosedRentalWhenDeletingCarThenPreservesHistory() throws Exception {
      UUID id = rent();
      giveBack(id, owner).andExpect(status().isOk());
      mvc.perform(delete("/cars/{id}", carId).header("Authorization", adminToken()))
          .andExpect(status().isConflict());
      mvc.perform(
              patch("/cars/{id}", carId)
                  .header("Authorization", adminToken())
                  .contentType("application/json")
                  .content("{\"available\":true,\"expectedVersion\":2}"))
          .andExpect(status().isOk());
    }

    @Test
    void givenRentalChangedVersionWhenSubmittingOldCarEditThenRejectsStaleEdit() throws Exception {
      rent();
      mvc.perform(
              patch("/cars/{id}", carId)
                  .header("Authorization", adminToken())
                  .contentType("application/json")
                  .content("{\"available\":true,\"expectedVersion\":0}"))
          .andExpect(status().isConflict());
    }
  }
}
