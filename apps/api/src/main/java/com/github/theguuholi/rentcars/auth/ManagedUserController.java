package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin users", description = "Administrator-only account management.")
class ManagedUserController {
  private final ManagedUsers users;

  ManagedUserController(ManagedUsers users) {
    this.users = users;
  }

  @GetMapping
  @Operation(summary = "List accounts")
  List<ManagedUser> list() {
    return users.list();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Read an account, including private driver details")
  ManagedUser get(@PathVariable UUID id) {
    return users.get(id);
  }

  @PostMapping
  @Operation(summary = "Create a driver account with an assigned role")
  @ApiResponse(responseCode = "201", description = "Account created")
  ResponseEntity<ManagedUser> create(@Valid @RequestBody ManagedUserCreateRequest input) {
    ManagedUser user = users.create(input);
    return ResponseEntity.created(URI.create("/admin/users/" + user.id())).body(user);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Replace editable account details")
  ManagedUser update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ManagedUserUpdateRequest input) {
    return users.update(UUID.fromString(jwt.getSubject()), id, input);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete an account without rental history")
  ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    users.delete(UUID.fromString(jwt.getSubject()), id);
    return ResponseEntity.noContent().build();
  }
}
