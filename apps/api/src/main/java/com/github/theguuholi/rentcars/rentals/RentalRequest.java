package com.github.theguuholi.rentcars.rentals;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@Schema(
    description = "Start an immediate rental. Identity, start time and charge are server-managed.")
public record RentalRequest(
    @NotNull
        @Schema(
            description = "Available vehicle identifier.",
            example = "2dfbdb0a-249e-42db-a0db-a4c066ffb067")
        UUID carId,
    @NotNull
        @Schema(
            description = "Return instant with UTC offset, at least 24 hours ahead.",
            example = "2026-11-08T15:00:00Z")
        Instant expectedReturnDate) {}
