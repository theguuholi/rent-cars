package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ManagedUserHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenService tokens;
  private UUID adminId;
  private String adminToken;

  @BeforeEach
  void admin() {
    adminId = account("ADMIN");
    adminToken = token(adminId, "ADMIN");
  }

  @AfterEach
  void clean() {
    jdbc.update(
        "delete from rentals where account_id in (select id from accounts where email like 'qa-managed-%')");
    jdbc.update("delete from accounts where email like 'qa-managed-%'");
  }

  private UUID account(String role) {
    return jdbc.queryForObject(
        "insert into accounts(email,password_hash,role) values (?,?,?) returning id",
        UUID.class,
        "qa-managed-" + UUID.randomUUID() + "@example.test",
        "unused",
        role);
  }

  private String token(UUID id, String role) {
    return tokens
        .issue(
            UsernamePasswordAuthenticationToken.authenticated(
                id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))))
        .accessToken();
  }

  private String newUser(String email, String role) {
    return json.writeValueAsString(
        Map.of(
            "role",
            role,
            "user",
            Map.of(
                "firstName", "Ada",
                "lastName", "Driver",
                "userName", "qa-" + UUID.randomUUID().toString().substring(0, 12),
                "driveLicense", "12345678901",
                "email", email,
                "password", "driver-password-123",
                "passwordConfirmation", "driver-password-123")));
  }

  private ResultActions create(String email) throws Exception {
    return mvc.perform(
        post("/admin/users")
            .header("Authorization", "Bearer " + adminToken)
            .contentType("application/json")
            .content(newUser(email, "USER")));
  }

  private String updateBody(String email, String role) {
    return json.writeValueAsString(
        Map.of(
            "email", email,
            "role", role,
            "firstName", "Updated",
            "lastName", "Driver",
            "userName", "qa-updated",
            "driveLicense", "12345678901"));
  }

  @Nested
  class ListUsers {
    @Test
    @DisplayName(
        "Given an admin, when listing accounts, then only public account fields are returned in email order")
    void list() throws Exception {
      mvc.perform(get("/admin/users").header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].email").exists())
          .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Given a driver or guest, when listing accounts, then access is denied")
    void restricted() throws Exception {
      mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
      UUID driver = account("USER");
      mvc.perform(get("/admin/users").header("Authorization", "Bearer " + token(driver, "USER")))
          .andExpect(status().isForbidden());
      String malformedSubject =
          tokens
              .issue(
                  UsernamePasswordAuthenticationToken.authenticated(
                      "not-a-uuid", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))
              .accessToken();
      mvc.perform(get("/admin/users").header("Authorization", "Bearer " + malformedSubject))
          .andExpect(status().isUnauthorized());
      jdbc.update("update accounts set role='USER' where id=?", adminId);
      mvc.perform(get("/admin/users").header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isForbidden());
      UUID target = account("USER");
      mvc.perform(get("/admin/users/" + target).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/admin/users")
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content(newUser("qa-managed-" + UUID.randomUUID() + "@example.test", "USER")))
          .andExpect(status().isForbidden());
      mvc.perform(
              put("/admin/users/" + target)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content("{\"email\":\"qa-managed-target@example.test\",\"role\":\"USER\"}"))
          .andExpect(status().isForbidden());
      mvc.perform(delete("/admin/users/" + target).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class CreateUser {
    @Test
    @DisplayName(
        "Given generated OpenAPI, when inspecting managed users, then the write and read schemas are documented")
    void openApi() throws Exception {
      mvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.paths['/admin/users'].post.responses['201']").exists())
          .andExpect(jsonPath("$.components.schemas.ManagedUser.properties.driveLicense").exists())
          .andExpect(
              jsonPath("$.components.schemas.ManagedUserUpdateRequest.properties.role").exists())
          .andExpect(
              jsonPath("$.components.schemas.ManagedUser.properties.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName(
        "Given an admin, when creating a driver, then the account has a location and can be read")
    void createAndRead() throws Exception {
      String email = "qa-managed-" + UUID.randomUUID() + "@example.test";
      String location =
          create(email)
              .andExpect(status().isCreated())
              .andExpect(header().exists("Location"))
              .andExpect(jsonPath("$.email").value(email))
              .andExpect(jsonPath("$.driveLicense").value("12345678901"))
              .andExpect(jsonPath("$.password").doesNotExist())
              .andReturn()
              .getResponse()
              .getHeader("Location");
      mvc.perform(get(location).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.firstName").value("Ada"));
      mvc.perform(
              get("/admin/users/" + UUID.randomUUID())
                  .header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "Given duplicate or invalid details, when creating a user, then no second account is saved")
    void invalid() throws Exception {
      String email = "qa-managed-" + UUID.randomUUID() + "@example.test";
      create(email).andExpect(status().isCreated());
      create(email).andExpect(status().isConflict());
      mvc.perform(
              post("/admin/users")
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content("{\"role\":\"ADMIN\",\"user\":{}}"))
          .andExpect(status().isUnprocessableContent());
      mvc.perform(
              post("/admin/users")
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content(newUser("qa-managed-" + UUID.randomUUID() + "@example.test", "ROOT")))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName(
        "Given an admin, when creating another administrator, then the assigned role is persisted")
    void createAdmin() throws Exception {
      mvc.perform(
              post("/admin/users")
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content(newUser("qa-managed-" + UUID.randomUUID() + "@example.test", "ADMIN")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.role").value("ADMIN"));
    }
  }

  @Nested
  class UpdateUser {
    @Test
    @DisplayName(
        "Given a driver, when an admin replaces account details, then normalized details are persisted")
    void update() throws Exception {
      String location =
          create("qa-managed-" + UUID.randomUUID() + "@example.test")
              .andReturn()
              .getResponse()
              .getHeader("Location");
      String email = "qa-managed-updated-" + UUID.randomUUID() + "@example.test";
      mvc.perform(
              put(location)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content(updateBody(email.toUpperCase(), "USER")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.role").value("USER"))
          .andExpect(jsonPath("$.firstName").value("Updated"));
      mvc.perform(get(location).header("Authorization", "Bearer " + adminToken))
          .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("Given incomplete driver details, when editing, then the account is unchanged")
    void guarded() throws Exception {
      String location =
          create("qa-managed-" + UUID.randomUUID() + "@example.test")
              .andReturn()
              .getResponse()
              .getHeader("Location");
      mvc.perform(
              put(location)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content("{\"email\":\"driver@example.test\",\"role\":\"USER\"}"))
          .andExpect(status().isUnprocessableContent());
      var fields =
          Map.of(
              "email", "qa-managed-edit@example.test",
              "role", "USER",
              "firstName", "Ada",
              "lastName", "Driver",
              "userName", "qa-managed-edit",
              "driveLicense", "12345678901");
      for (String missing : List.of("firstName", "lastName", "userName", "driveLicense")) {
        var body = new HashMap<>(fields);
        body.remove(missing);
        mvc.perform(
                put(location)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isUnprocessableContent());
      }
      for (String blank : List.of("firstName", "lastName")) {
        var body = new HashMap<>(fields);
        body.put(blank, " ");
        mvc.perform(
                put(location)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isUnprocessableContent());
      }
      mvc.perform(
              put(location)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content(updateBody("qa-managed-invalid@example.test", "ROOT")))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName(
        "Given an administrator without a driver profile, when editing, then profile fields remain absent")
    void adminWithoutDriverProfile() throws Exception {
      String email = "qa-managed-updated-" + UUID.randomUUID() + "@example.test";
      mvc.perform(
              put("/admin/users/" + adminId)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content(json.writeValueAsString(Map.of("email", email, "role", "ADMIN"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.firstName").value(org.hamcrest.Matchers.nullValue()));
      for (String unexpected : List.of("firstName", "lastName", "userName", "driveLicense")) {
        var body = new HashMap<>(Map.of("email", email, "role", "ADMIN"));
        body.put(
            unexpected,
            switch (unexpected) {
              case "userName" -> "qa-unexpected";
              case "driveLicense" -> "98765432109";
              default -> "Unexpected";
            });
        mvc.perform(
                put("/admin/users/" + adminId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isUnprocessableContent());
      }
      mvc.perform(
              put("/admin/users/" + adminId)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName(
        "Given another admin, when demoted, then its refresh sessions and old admin access stop working")
    void demoteAdministrator() throws Exception {
      UUID target = account("ADMIN");
      String oldToken = token(target, "ADMIN");
      jdbc.update(
          "insert into refresh_sessions(id,account_id,expires_at) values(?,?,now()+interval '1 day')",
          UUID.randomUUID(),
          target);
      String email = "qa-managed-demoted-" + UUID.randomUUID() + "@example.test";
      mvc.perform(
              put("/admin/users/" + target)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content(json.writeValueAsString(Map.of("email", email, "role", "USER"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value("USER"));
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from refresh_sessions where account_id=?",
                  Integer.class,
                  target))
          .isZero();
      mvc.perform(get("/admin/users").header("Authorization", "Bearer " + oldToken))
          .andExpect(status().isForbidden());
      mvc.perform(get("/cars/manage").header("Authorization", "Bearer " + oldToken))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
        "Given a driver, when promoted, then an old USER token cannot access administration")
    void promoteDriver() throws Exception {
      String location =
          create("qa-managed-" + UUID.randomUUID() + "@example.test")
              .andReturn()
              .getResponse()
              .getHeader("Location");
      UUID target = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
      String oldToken = token(target, "USER");
      mvc.perform(
              put(location)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content(
                      updateBody(
                          "qa-managed-promoted-" + UUID.randomUUID() + "@example.test", "ADMIN")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value("ADMIN"));
      mvc.perform(get("/admin/users").header("Authorization", "Bearer " + oldToken))
          .andExpect(status().isForbidden());
      mvc.perform(get("/admin/users").header("Authorization", "Bearer " + token(target, "ADMIN")))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given the current admin, when self-demoting, then keep the role")
    void selfDemotion() throws Exception {
      mvc.perform(
              put("/admin/users/" + adminId)
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType("application/json")
                  .content("{\"email\":\"qa-managed-self@example.test\",\"role\":\"USER\"}"))
          .andExpect(status().isConflict());
      assertThat(jdbc.queryForObject("select role from accounts where id=?", String.class, adminId))
          .isEqualTo("ADMIN");
    }
  }

  @Nested
  class DeleteUser {
    @Test
    @DisplayName("Given an unused account, when deleted by an admin, then the account is removed")
    void deleteUser() throws Exception {
      String location =
          create("qa-managed-" + UUID.randomUUID() + "@example.test")
              .andReturn()
              .getResponse()
              .getHeader("Location");
      mvc.perform(delete(location).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isNoContent());
      mvc.perform(get(location).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isNotFound());
      mvc.perform(delete("/admin/users/" + adminId).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isConflict());
      UUID anotherAdmin = account("ADMIN");
      String deletedToken = token(anotherAdmin, "ADMIN");
      mvc.perform(
              delete("/admin/users/" + anotherAdmin)
                  .header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isNoContent());
      mvc.perform(get("/cars/manage").header("Authorization", "Bearer " + deletedToken))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
        "Given rental history, when deleting the driver, then the history and account remain")
    void rentalHistory() throws Exception {
      String location =
          create("qa-managed-" + UUID.randomUUID() + "@example.test")
              .andReturn()
              .getResponse()
              .getHeader("Location");
      UUID user = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
      UUID car = jdbc.queryForObject("select id from cars limit 1", UUID.class);
      jdbc.update(
          "insert into rentals(car_id,account_id,start_date,expected_return_date,end_date,total_cents) values(?,?,now()-interval '3 days',now()-interval '1 day',now(),100)",
          car,
          user);
      mvc.perform(delete(location).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isConflict());
      assertThat(
              jdbc.queryForObject("select count(*) from accounts where id=?", Integer.class, user))
          .isEqualTo(1);
    }
  }
}
