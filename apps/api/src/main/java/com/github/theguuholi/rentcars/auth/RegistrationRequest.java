package com.github.theguuholi.rentcars.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Schema(description = "Details required to register a driver. Confirmation is never persisted.")
@PasswordConfirmed
record RegistrationRequest(
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,40}") String userName,
    @NotBlank @Pattern(regexp = "[0-9]{11}") String driveLicense,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 72) @Schema(format = "password") String password,
    @NotBlank @Size(max = 72) @Schema(format = "password") String passwordConfirmation)
    implements PasswordPair {
  RegistrationRequest {
    if (firstName != null) firstName = firstName.strip();
    if (lastName != null) lastName = lastName.strip();
    if (userName != null) userName = userName.strip().toLowerCase(Locale.ROOT);
    if (driveLicense != null) driveLicense = driveLicense.strip();
    if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
  }

  @JsonIgnore
  @AssertTrue(message = "Password must fit in 72 UTF-8 bytes")
  public boolean isPasswordWithinByteLimit() {
    return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  @Override
  public String toString() {
    return "RegistrationRequest[redacted]";
  }
}
