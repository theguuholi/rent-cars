package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

@Schema(description = "The account email to receive a password reset link, if registered.")
record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
  ForgotPasswordRequest {
    if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
  }
}
