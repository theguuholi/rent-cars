package com.github.theguuholi.rentcars.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

@Schema(
    description =
        "Email and password for a rental account. Public registration always assigns USER.")
public record Credentials(
    @NotBlank @Email @Size(max = 254) @Schema(example = "driver@example.com") String email,
    @NotBlank
        @Size(min = 12, max = 72)
        @Schema(
            format = "password",
            description = "At least 12 characters and at most 72 UTF-8 bytes.")
        String password) {
  public Credentials {
    if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return "Credentials[redacted]";
  }

  /** BCrypt limits input by byte length, including for multibyte characters. */
  @JsonIgnore
  @AssertTrue(message = "Password must fit in 72 UTF-8 bytes")
  public boolean isPasswordWithinByteLimit() {
    return Objects.toString(password, "").getBytes(StandardCharsets.UTF_8).length <= 72;
  }
}
