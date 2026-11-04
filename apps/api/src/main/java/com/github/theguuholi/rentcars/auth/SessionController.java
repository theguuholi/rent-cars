package com.github.theguuholi.rentcars.auth;

import com.github.theguuholi.rentcars.shared.http.GlobalExceptionHandler.Errors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
    name = "Accounts and sessions",
    description = "Register a driver, obtain an access token and read your profile.")
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
class SessionController {
  private final AccountRepository accounts;
  private final PasswordEncoder passwords;
  private final AuthenticationManager authentication;
  private final RefreshSessions tokens;

  SessionController(
      AccountRepository accounts,
      PasswordEncoder passwords,
      AuthenticationManager authentication,
      RefreshSessions tokens) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.authentication = authentication;
    this.tokens = tokens;
  }

  @GetMapping("/csrf")
  @Operation(summary = "Get a CSRF cookie and header for registration and sign-in")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  ResponseEntity<Void> csrf(
      @Parameter(hidden = true)
          @RequestAttribute("org.springframework.security.web.csrf.DeferredCsrfToken")
          DeferredCsrfToken deferred) {
    var token = deferred.get();
    return ResponseEntity.noContent()
        .cacheControl(CacheControl.noStore())
        .header(token.getHeaderName(), token.getToken())
        .build();
  }

  @ApiResponse(
      responseCode = "403",
      description = "Missing or invalid CSRF cookie/header pair",
      content = @Content)
  @PostMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Register a driver",
      description = "Creates a USER account with a unique, case-insensitive email.")
  @ApiResponse(
      responseCode = "201",
      description = "Public profile of the registered driver",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "Email already registered",
      content = @Content(schema = @Schema(implementation = Errors.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Invalid credentials format",
      content = @Content(schema = @Schema(implementation = Errors.class)))
  UserProfile register(@Valid @RequestBody Credentials input) {
    return accounts
        .saveAndFlush(new Account(input.email(), passwords.encode(input.password())))
        .profile();
  }

  @ApiResponse(
      responseCode = "403",
      description = "Missing or invalid CSRF cookie/header pair",
      content = @Content)
  @PostMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Sign in with email and password")
  @ApiResponse(
      responseCode = "200",
      description = "Access token for the next 15 minutes",
      useReturnTypeSchema = true)
  @ApiResponse(responseCode = "401", description = "Invalid email or password", content = @Content)
  @ApiResponse(
      responseCode = "422",
      description = "Invalid credentials format",
      content = @Content(schema = @Schema(implementation = Errors.class)))
  ResponseEntity<SessionToken> login(@Valid @RequestBody Credentials input) {
    try {
      var principal =
          authentication.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
      return granted(tokens.open(principal));
    } catch (BadCredentialsException _) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  private ResponseEntity<SessionToken> granted(RefreshSessions.Grant grant) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Set-Cookie", grant.cookie().toString())
        .body(grant.access());
  }

  @PostMapping("/sessions/refresh")
  @Operation(
      summary = "Rotate the refresh cookie and obtain a fresh access token",
      description =
          "Requires the CSRF cookie and X-XSRF-TOKEN header. Reusing a rotated cookie revokes this device session.")
  @ApiResponse(
      responseCode = "401",
      description = "Session missing, expired or revoked",
      content = @Content)
  @ApiResponse(
      responseCode = "200",
      description = "Replacement access token",
      useReturnTypeSchema = true)
  ResponseEntity<SessionToken> refresh(
      @CookieValue(name = RefreshSessions.COOKIE, defaultValue = "") String secret) {
    return tokens
        .rotate(secret)
        .map(this::granted)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }

  @DeleteMapping("/sessions/current")
  @Operation(
      summary = "Revoke this device session and clear the refresh cookie",
      description = "Requires the CSRF cookie and X-XSRF-TOKEN header.")
  @ApiResponse(responseCode = "204", description = "Session closed")
  ResponseEntity<Void> logout(
      @CookieValue(name = RefreshSessions.COOKIE, defaultValue = "") String secret) {
    tokens.revoke(secret);
    return ResponseEntity.noContent()
        .header("Set-Cookie", tokens.cookie("", Duration.ZERO).toString())
        .build();
  }

  @GetMapping(value = "/sessions/me", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Read the signed-in account",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponse(
      responseCode = "200",
      description = "Current public account profile",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "401",
      description = "Missing or invalid token, or account no longer exists",
      content = @Content)
  ResponseEntity<UserProfile> me(@AuthenticationPrincipal Jwt jwt) {
    return accounts
        .findById(UUID.fromString(jwt.getSubject()))
        .map(Account::profile)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }
}
