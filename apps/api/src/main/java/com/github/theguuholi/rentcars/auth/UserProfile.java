package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Public account details. Credentials are never included.")
public record UserProfile(
    @Schema(description = "Account identifier.") UUID id,
    @Schema(description = "Normalized email address.", example = "driver@example.com") String email,
    @Schema(
            description = "Current account role.",
            allowableValues = {"USER", "ADMIN"})
        String role,
    @Schema(description = "Driver's first name, when a profile exists.") String firstName,
    @Schema(description = "Driver's last name, when a profile exists.") String lastName,
    @Schema(description = "Normalized driver username, when a profile exists.") String userName) {}
