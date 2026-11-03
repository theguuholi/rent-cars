package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.*;

class CredentialsTest {
  @Nested
  class DiagnosticOutput {
    @Test
    @DisplayName(
        "Given credentials, when formatted for diagnostics, then the email and password are redacted")
    void redacted() {
      assertThat(new Credentials("driver@example.com", "local-secret-123"))
          .hasToString("Credentials[redacted]");
    }
  }

  @Nested
  class Normalize {
    @Test
    @DisplayName(
        "Given surrounding spaces and uppercase letters, when binding credentials, then only the email is normalized")
    void normalize() {
      var input = new Credentials(" Driver@EXAMPLE.COM ", "  Keep My Spaces!  ");
      assertThat(input.email()).isEqualTo("driver@example.com");
      assertThat(input.password()).isEqualTo("  Keep My Spaces!  ");
    }
  }

  @Nested
  class PasswordWithinByteLimit {
    @Test
    @DisplayName(
        "Given a multibyte password, when checking its size, then the limit counts UTF-8 bytes")
    void boundary() {
      assertThat(new Credentials(null, "€".repeat(24)).isPasswordWithinByteLimit()).isTrue();
      assertThat(new Credentials(null, "€".repeat(24) + "a").isPasswordWithinByteLimit()).isFalse();
    }
  }
}
