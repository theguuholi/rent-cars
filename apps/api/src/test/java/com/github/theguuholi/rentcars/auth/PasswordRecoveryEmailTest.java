package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordRecoveryEmailTest {
  @Test
  @DisplayName(
      "Given an address and token, when rendering the recovery email, then both formats contain the configured link")
  void message() throws IOException {
    var email = new PasswordRecoveryEmail("http://localhost:5173///");
    var message = email.message("driver@example.test", "sample-token");
    assertThat(message.to()).isEqualTo("driver@example.test");
    assertThat(message.subject()).isEqualTo("Reset your Rent Cars password");
    assertThat(message.text())
        .contains("http://localhost:5173/reset-password?token=sample-token", "30 minutes");
    assertThat(message.html())
        .contains(
            "href=\"http://localhost:5173/reset-password?token=sample-token\"", "Reset password");
    assertThat(message.html()).doesNotContain("{{resetUrl}}");
  }
}
