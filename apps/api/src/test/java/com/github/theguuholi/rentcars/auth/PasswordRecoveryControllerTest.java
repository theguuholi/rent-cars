package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import com.github.theguuholi.rentcars.shared.mail.EmailMessage;
import com.github.theguuholi.rentcars.shared.mail.EmailSender;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordRecoveryControllerTest {
  @Autowired MockMvc mvc;
  @Autowired AccountRepository accounts;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper json;
  @MockitoBean EmailSender sender;

  private final String address = UUID.randomUUID() + "@example.test";
  private final String oldPassword = "original-password-123";

  private void account() {
    accounts.saveAndFlush(new Account(address, passwords.encode(oldPassword)));
  }

  private String body(Map<String, String> fields) {
    return json.writeValueAsString(fields);
  }

  private ResultActions postJson(String path, String body) throws Exception {
    var csrf = mvc.perform(get("/csrf")).andReturn().getResponse();
    return mvc.perform(
        post(path)
            .cookie(csrf.getCookie("XSRF-TOKEN"))
            .header("X-XSRF-TOKEN", csrf.getHeader("X-XSRF-TOKEN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private String token() throws Exception {
    postJson("/password-recovery", body(Map.of("email", address))).andExpect(status().isAccepted());
    var captured = ArgumentCaptor.forClass(EmailMessage.class);
    verify(sender).send(captured.capture());
    var link = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(captured.getValue().text());
    assertThat(link.find()).isTrue();
    return link.group(1);
  }

  @Nested
  class Request {
    @Test
    @DisplayName(
        "Given a known email, when recovery is requested, then a private token is emailed and the response is generic")
    void known() throws Exception {
      account();
      var response =
          postJson("/password-recovery", body(Map.of("email", address.toUpperCase())))
              .andExpect(status().isAccepted())
              .andExpect(header().string("Cache-Control", "no-store"))
              .andExpect(content().string(""))
              .andReturn();
      var captured = ArgumentCaptor.forClass(EmailMessage.class);
      verify(sender).send(captured.capture());
      assertThat(captured.getValue().to()).isEqualTo(address);
      assertThat(captured.getValue().html()).contains("Reset password", "/reset-password?token=");
      assertThat(response.getResponse().getContentAsString()).doesNotContain("token");
      assertThat(db.queryForObject("select count(*) from password_reset_tokens", Integer.class))
          .isGreaterThan(0);
    }

    @Test
    @DisplayName(
        "Given an unknown email, when recovery is requested, then the same response reveals no account")
    void unknown() throws Exception {
      postJson("/password-recovery", body(Map.of("email", address)))
          .andExpect(status().isAccepted())
          .andExpect(content().string(""));
      verifyNoInteractions(sender);
    }

    @Test
    @DisplayName(
        "Given SMTP fails, when recovery is requested, then the response remains generic and no token remains")
    void mailFailure() throws Exception {
      account();
      doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any());
      postJson("/password-recovery", body(Map.of("email", address)))
          .andExpect(status().isAccepted());
      assertThat(
              db.queryForObject(
                  "select count(*) from password_reset_tokens where account_id=(select id from accounts where email=?)",
                  Integer.class,
                  address))
          .isZero();
    }

    @Test
    @DisplayName(
        "Given an invalid address or missing CSRF, when requesting recovery, then the API rejects the request")
    void invalid() throws Exception {
      postJson("/password-recovery", body(Map.of("email", "not-an-email")))
          .andExpect(status().isUnprocessableContent());
      postJson("/password-recovery", "{\"email\":null}")
          .andExpect(status().isUnprocessableContent());
      mvc.perform(
              post("/password-recovery")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(Map.of("email", address))))
          .andExpect(status().isForbidden());
      verifyNoInteractions(sender);
    }
  }

  @Nested
  class Reset {
    @Test
    @DisplayName(
        "Given a fresh token, when the password changes, then login uses the new password and the token is single-use")
    void successAndReplay() throws Exception {
      account();
      String secret = token();
      var session =
          postJson("/sessions", body(Map.of("email", address, "password", oldPassword)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getCookie(RefreshSessions.COOKIE);
      String request =
          body(
              Map.of(
                  "token",
                  secret,
                  "password",
                  "replacement-password-123",
                  "passwordConfirmation",
                  "replacement-password-123"));
      postJson("/password-recovery/reset", request)
          .andExpect(status().isNoContent())
          .andExpect(header().string("Cache-Control", "no-store"));
      postJson("/sessions", body(Map.of("email", address, "password", oldPassword)))
          .andExpect(status().isUnauthorized());
      postJson("/sessions", body(Map.of("email", address, "password", "replacement-password-123")))
          .andExpect(status().isOk());
      var csrf = mvc.perform(get("/csrf")).andReturn().getResponse();
      mvc.perform(
              post("/sessions/refresh")
                  .cookie(session, csrf.getCookie("XSRF-TOKEN"))
                  .header("X-XSRF-TOKEN", csrf.getHeader("X-XSRF-TOKEN")))
          .andExpect(status().isUnauthorized());
      postJson("/password-recovery/reset", request).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given an expired token, when resetting, then the old password remains valid")
    void expired() throws Exception {
      account();
      String secret = token();
      db.update(
          "update password_reset_tokens set expires_at=now()-interval '1 second' where account_id=(select id from accounts where email=?)",
          address);
      postJson(
              "/password-recovery/reset",
              body(
                  Map.of(
                      "token",
                      secret,
                      "password",
                      "replacement-password-123",
                      "passwordConfirmation",
                      "replacement-password-123")))
          .andExpect(status().isBadRequest());
      postJson("/sessions", body(Map.of("email", address, "password", oldPassword)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "Given a second request, when the first token is used, then only the latest link works")
    void replaced() throws Exception {
      account();
      String first = token();
      reset(sender);
      String second = token();
      assertThat(second).isNotEqualTo(first);
      postJson(
              "/password-recovery/reset",
              body(
                  Map.of(
                      "token",
                      first,
                      "password",
                      "replacement-password-123",
                      "passwordConfirmation",
                      "replacement-password-123")))
          .andExpect(status().isBadRequest());
      postJson(
              "/password-recovery/reset",
              body(
                  Map.of(
                      "token",
                      second,
                      "password",
                      "replacement-password-123",
                      "passwordConfirmation",
                      "replacement-password-123")))
          .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName(
        "Given a short, mismatched or oversized password, when resetting, then validation rejects the request")
    void invalidPassword() throws Exception {
      for (String[] passwords :
          new String[][] {
            {"short", "short"},
            {"replacement-password-123", "different-password-123"},
            {"é".repeat(37), "é".repeat(37)}
          }) {
        postJson(
                "/password-recovery/reset",
                body(
                    Map.of(
                        "token",
                        "invalid",
                        "password",
                        passwords[0],
                        "passwordConfirmation",
                        passwords[1])))
            .andExpect(status().isUnprocessableContent());
      }
      postJson(
              "/password-recovery/reset",
              "{\"token\":\"invalid\",\"password\":null,\"passwordConfirmation\":null}")
          .andExpect(status().isUnprocessableContent());
      assertThat(new ResetPasswordRequest("private-token", "private-password", "private-password"))
          .hasToString("ResetPasswordRequest[redacted]");
    }
  }
}
