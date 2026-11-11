package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

@Schema(
    description =
        "Complete replacement of editable account details. Password changes use recovery.")
record ManagedUserUpdateRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Pattern(regexp = "USER|ADMIN") String role,
    @Size(max = 80) String firstName,
    @Size(max = 80) String lastName,
    @Pattern(regexp = "[a-z0-9._-]{3,40}") String userName,
    @Pattern(regexp = "[0-9]{11}") String driveLicense) {
  ManagedUserUpdateRequest {
    if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    if (firstName != null) firstName = firstName.strip();
    if (lastName != null) lastName = lastName.strip();
    if (userName != null) userName = userName.strip().toLowerCase(Locale.ROOT);
    if (driveLicense != null) driveLicense = driveLicense.strip();
  }
}
