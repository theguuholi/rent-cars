package com.github.theguuholi.recommendations.rentals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Consumer-owned envelope of the versioned contract in docs/events/rentals.md. */
@JsonIgnoreProperties(ignoreUnknown = true)
record RentalEvent(
    @NotNull UUID eventId,
    @NotNull @Pattern(regexp = "RentalOpened|RentalReturned") String eventType,
    @NotNull @Min(1) @Max(1) Integer schemaVersion,
    @NotNull Instant occurredAt,
    @NotNull UUID rentalId,
    @NotNull JsonNode payload) {}
