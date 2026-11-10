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
    var bootstrap =
        mvc.perform(get("/csrf"))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse();
    return mvc.perform(
        post(path)
            .cookie(bootstrap.getCookie("XSRF-TOKEN"))
            .header("X-XSRF-TOKEN", bootstrap.getHeader("X-XSRF-TOKEN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  String credentials(String email, String password) {
    return json.writeValueAsString(Map.of("email", email, "password", password));
  }

  String registration(String email, String password) {
    return registration(
        email,
        password,
        "qa-" + UUID.randomUUID().toString().substring(0, 12),
        String.valueOf(
            java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(10_000_000_000L, 100_000_000_000L)));
  }

  String registration(String email, String password, String userName, String driveLicense) {
    return json.writeValueAsString(
        Map.of(
            "firstName",
            "Test",
            "lastName",
            "Driver",
            "userName",
            userName,
            "driveLicense",
            driveLicense,
            "email",
            email,
            "password",
            password,
            "passwordConfirmation",
            password));
  }

  String register() throws Exception {
    var response =
        send("/users", registration(email, password)).andExpect(status().isCreated()).andReturn();
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
  class Refresh {
    jakarta.servlet.http.Cookie signInCookie() throws Exception {
      return send("/sessions", credentials(email, password))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getCookie(RefreshSessions.COOKIE);
    }

    ResultActions exchange(jakarta.servlet.http.Cookie cookie, boolean logout) throws Exception {
      var csrf = mvc.perform(get("/csrf")).andReturn().getResponse();
      return mvc.perform(
          (logout ? delete("/sessions/current") : post("/sessions/refresh"))
              .cookie(cookie, csrf.getCookie("XSRF-TOKEN"))
              .header("X-XSRF-TOKEN", csrf.getHeader("X-XSRF-TOKEN")));
    }

    @Test
    @DisplayName(
        "Given two concurrent refreshes, when both submit the same cookie, then one rotates and replay revokes only that device")
    void replay() throws Exception {
      register();
      var cookie = signInCookie();
      var other = signInCookie();
      var start = new java.util.concurrent.CyclicBarrier(2);
      try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        java.util.concurrent.Callable<org.springframework.mock.web.MockHttpServletResponse> call =
            () -> {
              start.await(10, java.util.concurrent.TimeUnit.SECONDS);
              return exchange(cookie, false).andReturn().getResponse();
            };
        var first = workers.submit(call);
        var second = workers.submit(call);
        var a = first.get();
        var b = second.get();
        assertThat(List.of(a.getStatus(), b.getStatus())).containsExactlyInAnyOrder(200, 401);
        var successor = (a.getStatus() == 200 ? a : b).getCookie(RefreshSessions.COOKIE);
        assertThat(successor.getValue()).isNotEqualTo(cookie.getValue());
        exchange(successor, false).andExpect(status().isUnauthorized());
        exchange(other, false).andExpect(status().isOk());
      }
    }

    @Test
    @DisplayName(
        "Given a refresh session near absolute expiry, when it rotates, then only the digest is persisted and the new cookie inherits the remaining lifetime")
    void absoluteExpiry() throws Exception {
      var id = UUID.fromString(register());
      var first = signInCookie();
      byte[] digest = org.apache.commons.codec.digest.DigestUtils.sha256(first.getValue());
      assertThat(
              jdbc.queryForObject(
                  "select digest from refresh_tokens where session_id=(select id from refresh_sessions where account_id=?)",
                  byte[].class,
                  id))
          .isEqualTo(digest);
      jdbc.update(
          "update refresh_sessions set expires_at=now()+interval '3 minutes' where account_id=?",
          id);
      var response = exchange(first, false).andExpect(status().isOk()).andReturn().getResponse();
      assertThat(response.getCookie(RefreshSessions.COOKIE).getMaxAge()).isBetween(1, 180);
    }

    @Test
    @DisplayName(
        "Given a role change, when a valid session rotates, then the JWT reflects the current account and logout disables renewal")
    void permissionsAndLogout() throws Exception {
      register();
      var cookie = signInCookie();
      assertThat(cookie.isHttpOnly()).isTrue();
      assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
      jdbc.update("update accounts set role='ADMIN' where email=?", email);
      var response = exchange(cookie, false).andExpect(status().isOk()).andReturn().getResponse();
      var jwt =
          decoder.decode(
              json.readTree(response.getContentAsString()).get("accessToken").asString());
      assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
      var next = response.getCookie(RefreshSessions.COOKIE);
      exchange(next, true)
          .andExpect(status().isNoContent())
          .andExpect(cookie().maxAge(RefreshSessions.COOKIE, 0));
      exchange(next, false).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Given an expired session, when its cookie is presented, then it cannot renew")
    void expired() throws Exception {
      var id = register();
      var cookie = signInCookie();
      jdbc.update(
          "update refresh_sessions set expires_at=now() where account_id=?", UUID.fromString(id));
      exchange(cookie, false).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
        "Given a bearer header but no CSRF proof, when rotating or logging out, then both cookie operations are forbidden")
    void csrfRequired() throws Exception {
      register();
      var bearer = "Bearer " + login();
      var cookie = signInCookie();
      mvc.perform(post("/sessions/refresh").cookie(cookie).header("Authorization", bearer))
          .andExpect(status().isForbidden());
      mvc.perform(delete("/sessions/current").cookie(cookie).header("Authorization", bearer))
          .andExpect(status().isForbidden());
    }
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
          jdbc.queryForObject("select id::text from categories where name='SEDAN'", String.class);
      var gps =
          jdbc.queryForObject(
              "insert into specifications(name,description) values ('TEST GPS','Turn-by-turn directions') returning id::text",
              String.class);
      var abs =
          jdbc.queryForObject(
              "insert into specifications(name,description) values ('TEST ABS','Anti-lock braking') returning id::text",
              String.class);
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
                  category,
                  "specificationIds",
                  List.of(gps, abs)));
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
          .andExpect(jsonPath("$.licensePlate").value("JWT1234"))
          .andExpect(jsonPath("$.category.name").value("SEDAN"))
          .andExpect(jsonPath("$.categoryId").value(category))
          .andExpect(jsonPath("$.category.id").value(category))
          .andExpect(
              jsonPath("$.category.description")
                  .value("Car with a trunk separate from the passenger compartment."))
          .andExpect(jsonPath("$.specifications.length()").value(2))
          .andExpect(jsonPath("$.specifications[0].id").value(abs))
          .andExpect(jsonPath("$.specifications[0].name").value("TEST ABS"))
          .andExpect(jsonPath("$.specifications[1].id").value(gps))
          .andExpect(jsonPath("$.specifications[1].name").value("TEST GPS"))
          .andExpect(
              jsonPath("$.specifications[*].description")
                  .value(Matchers.contains("Anti-lock braking", "Turn-by-turn directions")));
      mvc.perform(get("/categories")).andExpect(status().isUnauthorized());
      mvc.perform(get("/specifications")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class Register {
    @Test
    @DisplayName(
        "Given a cross-site browser request without cookies, when registering without a CSRF token, then the signup is rejected")
    void rejectsCrossSiteSignup() throws Exception {
      mvc.perform(
              post("/users")
                  .header("Sec-Fetch-Site", "cross-site")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registration(email, password)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
        "Given a browser cookie without a CSRF token, when registering, then the request is forbidden and the account is not created")
    void rejectsUnprotectedCookieWrite() throws Exception {
      mvc.perform(
              post("/users")
                  .cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "example"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registration(email, password)))
          .andExpect(status().isForbidden());
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from accounts where email=?", Integer.class, email))
          .isZero();
    }

    @Test
    @DisplayName(
        "Given a mixed-case email and an injected admin role, when registering, then only the normalized USER profile is exposed and the password is hashed")
    void publicProfile() throws Exception {
      var body =
          json.writeValueAsString(
              Map.of(
                  "firstName",
                  " Test ",
                  "lastName",
                  " Driver ",
                  "userName",
                  " QA-" + UUID.randomUUID().toString().substring(0, 12) + " ",
                  "driveLicense",
                  "12345678901",
                  "email",
                  " " + email.toUpperCase(Locale.ROOT) + " ",
                  "password",
                  password,
                  "passwordConfirmation",
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
          .containsExactlyInAnyOrder("id", "email", "role", "firstName", "lastName", "userName");
      assertThat(
              json.readTree(result.getResponse().getContentAsString()).get("firstName").asString())
          .isEqualTo("Test");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from driver_profiles where account_id=(select id from accounts where email=?) and first_name='Test' and last_name='Driver' and drive_license='12345678901'",
                  Integer.class,
                  email))
          .isOne();
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
      send("/users", registration(email.toUpperCase(Locale.ROOT), password))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.errors.conflict").isArray());
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from accounts where email=?", Integer.class, email))
          .isOne();
    }

    @ParameterizedTest(
        name = "Given an existing {0}, when registering again, then the database keeps one driver")
    @ValueSource(strings = {"user_name", "drive_license"})
    void duplicateDriverIdentifier(String column) throws Exception {
      var id = UUID.fromString(register());
      var existing =
          jdbc.queryForObject(
              "select " + column + " from driver_profiles where account_id=?", String.class, id);
      var username =
          column.equals("user_name")
              ? existing.toUpperCase(Locale.ROOT)
              : "qa-" + UUID.randomUUID().toString().substring(0, 12);
      var license = column.equals("drive_license") ? existing : "98765432109";
      send("/users", registration("another-" + email, password, username, license))
          .andExpect(status().isConflict())
          .andExpect(
              jsonPath("$.errors.conflict[0]")
                  .value("A record with the same unique value already exists"));
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from driver_profiles where " + column + "=?",
                  Integer.class,
                  existing))
          .isOne();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from accounts where email=?", Integer.class, "another-" + email))
          .isZero();
    }

    @ParameterizedTest(
        name =
            "Given simultaneous registrations with the same {0}, when both save, then one succeeds and one conflicts")
    @ValueSource(strings = {"user_name", "drive_license"})
    void concurrentIdentifier(String column) throws Exception {
      var sharedUsername = "race-" + UUID.randomUUID().toString().substring(0, 12);
      var sharedLicense =
          String.valueOf(
              java.util.concurrent.ThreadLocalRandom.current()
                  .nextLong(10_000_000_000L, 100_000_000_000L));
      var start = new java.util.concurrent.CyclicBarrier(2);
      try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        java.util.concurrent.Callable<Integer> first =
            () -> {
              start.await(10, java.util.concurrent.TimeUnit.SECONDS);
              return send(
                      "/users",
                      registration("one-" + email, password, sharedUsername, sharedLicense))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            };
        java.util.concurrent.Callable<Integer> second =
            () -> {
              start.await(10, java.util.concurrent.TimeUnit.SECONDS);
              return send(
                      "/users",
                      registration(
                          "two-" + email,
                          password,
                          column.equals("user_name") ? sharedUsername : "other-" + sharedUsername,
                          column.equals("drive_license") ? sharedLicense : "98765432108"))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            };
        var a = workers.submit(first);
        var b = workers.submit(second);
        assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
      }
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from driver_profiles where " + column + "=?",
                  Integer.class,
                  column.equals("user_name") ? sharedUsername : sharedLicense))
          .isOne();
    }

    @Test
    @DisplayName(
        "Given different passwords, when registering, then the API rejects confirmation without saving either record")
    void confirmationMismatch() throws Exception {
      var body =
          registration(email, password)
              .replace(
                  "\"passwordConfirmation\":\"" + password + "\"",
                  "\"passwordConfirmation\":\"different-password-123\"");
      var response =
          send("/users", body)
              .andExpect(status().isUnprocessableContent())
              .andExpect(jsonPath("$.errors.passwordConfirmation").isArray())
              .andReturn()
              .getResponse();
      assertThat(json.readTree(response.getContentAsString()).get("errors").propertyNames())
          .containsExactly("passwordConfirmation");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from accounts where email=?", Integer.class, email))
          .isZero();
    }

    @ParameterizedTest(name = "Given invalid {0}, when registering, then the API rejects the field")
    @CsvSource({
      "firstName,Test,'   '",
      "lastName,Driver,'   '",
      "userName,valid.name,x",
      "driveLicense,12345678901,123",
      "email,valid@example.test,bad"
    })
    void invalidDriverField(String field, String original, String invalid) throws Exception {
      String body =
          registration("valid@example.test", password, "valid.name", "12345678901")
              .replace(
                  "\"" + field + "\":\"" + original + "\"",
                  "\"" + field + "\":\"" + invalid + "\"");
      send("/users", body)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.errors." + field).isArray());
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
      send("/users", registration(email, "é".repeat(37)))
          .andExpect(status().isUnprocessableContent());
      send("/users", registration(email, "é".repeat(36))).andExpect(status().isCreated());
    }
  }

  @Nested
  class Login {
    @Test
    @DisplayName(
        "Given the development seed, when signing in with the documented credentials, then the account has the ADMIN role and can read the protected catalog")
    void seededAdmin() throws Exception {
      email = "test@test";
      password = "test@tesT@dev";
      var bearer = "Bearer " + login();
      mvc.perform(get("/sessions/me").header("Authorization", bearer))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.role").value("ADMIN"));
      mvc.perform(get("/categories").header("Authorization", bearer)).andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "Given a CSRF cookie, when signing in with a mismatched header, then the API rejects the request before checking credentials")
    void rejectsMismatchedToken() throws Exception {
      var bootstrap = mvc.perform(get("/csrf")).andReturn().getResponse();
      mvc.perform(
              post("/sessions")
                  .cookie(bootstrap.getCookie("XSRF-TOKEN"))
                  .header("X-XSRF-TOKEN", "different")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(credentials(email, password)))
          .andExpect(status().isForbidden());
    }

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
      String username =
          jdbc.queryForObject(
              "select user_name from driver_profiles where account_id=?",
              String.class,
              UUID.fromString(id));
      email = "another-" + email;
      register();
      mvc.perform(get("/sessions/me").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(
              content()
                  .json(
                      json.writeValueAsString(
                          Map.of(
                              "id",
                              id,
                              "role",
                              "ADMIN",
                              "email",
                              original,
                              "firstName",
                              "Test",
                              "lastName",
                              "Driver",
                              "userName",
                              username)),
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
              jsonPath("$.components.schemas.RegistrationRequest.properties.firstName").exists())
          .andExpect(
              jsonPath(
                      "$.components.schemas.RegistrationRequest.properties.passwordConfirmation.format")
                  .value("password"))
          .andExpect(
              jsonPath("$.components.schemas.UserProfile.properties.id.format").value("uuid"))
          .andExpect(
              jsonPath("$.components.schemas.UserProfile.properties.passwordHash").doesNotExist())
          .andExpect(
              jsonPath("$.components.schemas.UserProfile.properties.driveLicense").doesNotExist());
    }
  }
}
