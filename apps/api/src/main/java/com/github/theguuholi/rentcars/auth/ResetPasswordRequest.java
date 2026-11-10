package com.github.theguuholi.rentcars.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

@Schema(description = "A single-use link token and a new confirmed password.")
@PasswordConfirmed
record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 12, max = 72) @Schema(format = "password") String password,
    @NotBlank @Size(max = 72) @Schema(format = "password") String passwordConfirmation)
    implements PasswordPair {
  @JsonIgnore
  @AssertTrue(message = "Password must fit in 72 UTF-8 bytes")
  public boolean isPasswordWithinByteLimit() {
    return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  @Override
  public String toString() {
    return "ResetPasswordRequest[redacted]";
  }
}
