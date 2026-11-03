package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired JwtDecoder decoder;
  @Autowired PasswordEncoder passwords;
  String email = UUID.randomUUID() + "@example.com";
  String password = "driver-example-123";

  ResultActions send(String path, String body) throws Exception {
    return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  String credentials(String email, String password) {
    return json.writeValueAsString(Map.of("email", email, "password", password));
  }

  String register() throws Exception {
    var response =
        send("/users", credentials(email, password)).andExpect(status().isCreated()).andReturn();
    return json.readTree(response.getResponse().getContentAsString()).get("id").asString();
  }

  String login() throws Exception {
    var response =
        send("/sessions", credentials(email.toUpperCase(Locale.ROOT), password))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();
    return json.readTree(response.getResponse().getContentAsString()).get("accessToken").asString();
  }

  @Nested
  class CatalogAuthorization {
    @ParameterizedTest(
        name =
            "Given a catalog route {0} {1}, when called anonymously or by a driver, then it returns 401 or 403 respectively")
    @CsvSource({
      "POST,/cars",
      "PATCH,/cars/{id}",
      "DELETE,/cars/{id}",
      "GET,/categories",
      "GET,/specifications",
      "GET,/specifications/{id}",
      "POST,/specifications",
      "PUT,/specifications/{id}",
      "PATCH,/specifications/{id}",
      "DELETE,/specifications/{id}"
    })
    void adminOnly(String method, String path) throws Exception {
      var verb = org.springframework.http.HttpMethod.valueOf(method);
      var url = java.net.URI.create(path.replace("{id}", UUID.randomUUID().toString()));
      mvc.perform(request(verb, url)).andExpect(status().isUnauthorized());
      register();
      mvc.perform(request(verb, url).header("Authorization", "Bearer " + login()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
        "Given an admin account, when it signs in and creates a car, then an anonymous visitor can list it and read its details")
    void browseFleet() throws Exception {
      register();
      jdbc.update("update accounts set role='ADMIN' where email=?", email);
      var category =
          jdbc.queryForObject(
              "select id::text from categories order by name limit 1", String.class);
      var body =
          json.writeValueAsString(
              Map.of(
                  "name",
                  "Public fleet car",
                  "brand",
                  "Example",
                  "description",
                  "Test car",
                  "licensePlate",
                  "JWT1234",
                  "dailyRateCents",
                  100,
                  "fineAmountCents",
                  0,
                  "categoryId",
                  category));
      var location =
          mvc.perform(
                  post("/cars")
                      .header("Authorization", "Bearer " + login())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getHeader("Location");
      mvc.perform(get("/cars"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].licensePlate").value(Matchers.hasItem("JWT1234")));
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.licensePlate").value("JWT1234"));
    }
  }

  @Nested
  class Register {
    @Test
    @DisplayName(
        "Given a mixed-case email and an injected admin role, when registering, then only the normalized USER profile is exposed and the password is hashed")
    void publicProfile() throws Exception {
      var body =
          json.writeValueAsString(
              Map.of(
                  "email",
                  " " + email.toUpperCase(Locale.ROOT) + " ",
                  "password",
                  password,
                  "role",
                  "ADMIN"));
      var result =
          send("/users", body)
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.role").value("USER"))
              .andExpect(jsonPath("$.email").value(email))
              .andReturn();
      assertThat(json.readTree(result.getResponse().getContentAsString()).propertyNames())
          .containsExactlyInAnyOrder("id", "email", "role");
      assertThat(
              passwords.matches(
                  password,
                  jdbc.queryForObject(
                      "select password_hash from accounts where email=?", String.class, email)))
          .isTrue();
    }

    @Test
    @DisplayName(
        "Given a registered email, when registering another casing, then PostgreSQL preserves one account and the API returns conflict")
    void duplicate() throws Exception {
      register();
      send("/users", credentials(email.toUpperCase(Locale.ROOT), password))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.errors.conflict").isArray());
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from accounts where email=?", Integer.class, email))
          .isOne();
    }

    @ParameterizedTest(name = "Given {0}, when registering, then validation rejects the request")
    @ValueSource(
        strings = {
          "{\"email\":\"bad\",\"password\":\"long-enough-secret\"}",
          "{}",
          "{\"email\":\"a@b.com\",\"password\":\"short\"}"
        })
    void invalid(String body) throws Exception {
      send("/users", body).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName(
        "Given multibyte passwords at and above the BCrypt limit, when registering, then only the 72-byte password succeeds")
    void byteBoundary() throws Exception {
      send("/users", credentials(email, "é".repeat(37)))
          .andExpect(status().isUnprocessableContent());
      send("/users", credentials(email, "é".repeat(36))).andExpect(status().isCreated());
    }
  }

  @Nested
  class Login {
    @Test
    @DisplayName(
        "Given valid credentials, when signing in, then the signed token identifies the account, issuer, audience, role and fifteen-minute lifetime")
    void claims() throws Exception {
      var id = register();
      var jwt = decoder.decode(login());
      assertThat(jwt.getSubject()).isEqualTo(id);
      assertThat(jwt.getClaimAsString("iss")).isEqualTo("rent-cars");
      assertThat(jwt.getAudience()).containsExactly("rent-cars-api");
      assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
      assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
          .isEqualTo(Duration.ofMinutes(15));
      assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    @DisplayName(
        "Given an unknown account or a wrong password, when signing in, then both return the same empty 401")
    void invalid() throws Exception {
      register();
      for (String candidate : List.of(email, "missing-" + email)) {
        send("/sessions", credentials(candidate, "wrong-password"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(""));
      }
    }

    @Test
    @DisplayName(
        "Given missing credentials, when signing in, then both fields report validation errors")
    void missing() throws Exception {
      send("/sessions", "{}")
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.errors.email").isArray())
          .andExpect(jsonPath("$.errors.password").isArray());
    }
  }

  @Nested
  class Me {
    @Test
    @DisplayName(
        "Given two drivers, when the first presents a token, then only that driver's current public profile is returned")
    void ownProfile() throws Exception {
      String id = register();
      String token = login();
      jdbc.update("update accounts set role='ADMIN' where email=?", email);
      String original = email;
      email = "another-" + email;
      register();
      mvc.perform(get("/sessions/me").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(
              content()
                  .json(
                      json.writeValueAsString(Map.of("id", id, "role", "ADMIN", "email", original)),
                      JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName(
        "Given a deleted account, when its valid token is presented, then the profile is unauthorized")
    void deleted() throws Exception {
      register();
      String token = login();
      jdbc.update("delete from accounts where email=?", email);
      mvc.perform(get("/sessions/me").header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
        "Given no token or a malformed token, when reading the profile, then the Bearer filter returns 401")
    void unauthorized() throws Exception {
      mvc.perform(get("/sessions/me"))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string("WWW-Authenticate", Matchers.startsWith("Bearer")));
      mvc.perform(get("/sessions/me").header("Authorization", "Bearer broken"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class OpenApi {
    @Test
    @DisplayName(
        "Given the published API, when reading the contract, then credentials, session token, public profile and protected operation are documented")
    void schemas() throws Exception {
      mvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
          .andExpect(jsonPath("$.paths['/cars'].get.security").doesNotExist())
          .andExpect(jsonPath("$.paths['/cars/{id}'].get.security").doesNotExist())
          .andExpect(jsonPath("$.paths['/cars'].post.security[0].bearerAuth").isArray())
          .andExpect(jsonPath("$.paths['/cars/{id}'].patch.security[0].bearerAuth").isArray())
          .andExpect(jsonPath("$.paths['/cars/{id}'].delete.security[0].bearerAuth").isArray())
          .andExpect(jsonPath("$.paths['/categories'].get.security[0].bearerAuth").isArray())
          .andExpect(jsonPath("$.paths['/specifications'].get.security[0].bearerAuth").isArray())
          .andExpect(jsonPath("$.paths['/sessions/me'].get.security[0].bearerAuth").isArray())
          .andExpect(jsonPath("$.paths['/sessions'].post.security").doesNotExist())
          .andExpect(
              jsonPath(
                      "$.paths['/users'].post.responses['201'].content['application/json'].schema['$ref']")
                  .value("#/components/schemas/UserProfile"))
          .andExpect(
              jsonPath(
                      "$.paths['/sessions'].post.responses['200'].content['application/json'].schema['$ref']")
                  .value("#/components/schemas/SessionToken"))
          .andExpect(
              jsonPath("$.components.schemas.Credentials.properties.password.format")
                  .value("password"))
          .andExpect(
              jsonPath("$.components.schemas.UserProfile.properties.id.format").value("uuid"))
          .andExpect(
              jsonPath("$.components.schemas.UserProfile.properties.passwordHash").doesNotExist());
    }
  }
}
