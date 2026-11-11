package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "A new driver account and its assigned role.")
record ManagedUserCreateRequest(
    @NotNull @Valid RegistrationRequest user,
    @NotNull @Pattern(regexp = "USER|ADMIN") String role) {}
