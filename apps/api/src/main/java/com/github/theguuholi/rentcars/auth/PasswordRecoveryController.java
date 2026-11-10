package com.github.theguuholi.rentcars.auth;

import com.github.theguuholi.rentcars.shared.http.GlobalExceptionHandler.Errors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
    name = "Password recovery",
    description = "Request and consume a single-use password reset link.")
class PasswordRecoveryController {
  private final PasswordRecovery recovery;

  PasswordRecoveryController(PasswordRecovery recovery) {
    this.recovery = recovery;
  }

  @PostMapping("/password-recovery")
  @Operation(
      summary = "Request a reset link",
      description =
          "Always returns 202 for a valid email format, whether or not the account exists. Requires a CSRF cookie and header.")
  @ApiResponse(
      responseCode = "202",
      description = "Request accepted without disclosing account existence")
  @ApiResponse(
      responseCode = "422",
      description = "Invalid email format",
      content = @Content(schema = @Schema(implementation = Errors.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Missing or invalid CSRF cookie/header pair",
      content = @Content)
  ResponseEntity<Void> request(@Valid @RequestBody ForgotPasswordRequest input) {
    recovery.request(input.email());
    return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore()).build();
  }

  @PostMapping("/password-recovery/reset")
  @Operation(
      summary = "Choose a new password",
      description =
          "Consumes a link token once and revokes refresh sessions. Requires a CSRF cookie and header.")
  @ApiResponse(responseCode = "204", description = "Password changed")
  @ApiResponse(
      responseCode = "400",
      description = "Link invalid, expired or already used",
      content = @Content)
  @ApiResponse(
      responseCode = "422",
      description = "Invalid password or confirmation",
      content = @Content(schema = @Schema(implementation = Errors.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Missing or invalid CSRF cookie/header pair",
      content = @Content)
  ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest input) {
    if (!recovery.reset(input.token(), input.password())) {
      return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
    }
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
