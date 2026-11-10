package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegistrationRequestTest {
  RegistrationRequest request(String password, String confirmation) {
    return new RegistrationRequest(
        " Test ",
        " Driver ",
        " TEST.Driver ",
        " 01234567890 ",
        " Driver@Example.test ",
        password,
        confirmation);
  }

  @Nested
  class Normalize {
    @Test
    @DisplayName(
        "Given padded fields, when constructing registration input, then identifiers are normalized and secrets are redacted")
    void fields() {
      var input = request("my-password-123", "my-password-123");
      assertThat(input.firstName()).isEqualTo("Test");
      assertThat(input.lastName()).isEqualTo("Driver");
      assertThat(input.userName()).isEqualTo("test.driver");
      assertThat(input.driveLicense()).isEqualTo("01234567890");
      assertThat(input.email()).isEqualTo("driver@example.test");
      assertThat(input.toString()).isEqualTo("RegistrationRequest[redacted]");
      var missing = new RegistrationRequest(null, null, null, null, null, null, null);
      assertThat(missing.firstName()).isNull();
      assertThat(missing.lastName()).isNull();
      assertThat(missing.userName()).isNull();
      assertThat(missing.driveLicense()).isNull();
      assertThat(missing.email()).isNull();
    }
  }

  @Nested
  class PasswordWithinByteLimit {
    @Test
    @DisplayName(
        "Given a multibyte password, when checking BCrypt's limit, then 72 bytes pass and 73 fail")
    void boundary() {
      assertThat(request("é".repeat(36), "é".repeat(36)).isPasswordWithinByteLimit()).isTrue();
      assertThat(request("é".repeat(36) + "a", "é".repeat(36) + "a").isPasswordWithinByteLimit())
          .isFalse();
      assertThat(request(null, null).isPasswordWithinByteLimit()).isTrue();
    }
  }

  @Nested
  class PasswordConfirmation {
    @Test
    @DisplayName(
        "Given two passwords, when checking confirmation, then a mismatch belongs to passwordConfirmation")
    void equality() {
      try (var factory = Validation.buildDefaultValidatorFactory()) {
        var validator = factory.getValidator();
        assertThat(validator.validate(request("my-password-123", "my-password-123"))).isEmpty();
        assertThat(validator.validate(request("my-password-123", "my-password-123 ")))
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("passwordConfirmation");
      }
      var check = new PasswordConfirmationValidator();
      assertThat(check.isValid(null, null)).isTrue();
      assertThat(check.isValid(request(null, "my-password-123"), null)).isTrue();
      assertThat(check.isValid(request("my-password-123", null), null)).isTrue();
    }
  }
}
