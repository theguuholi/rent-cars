package com.github.theguuholi.rentcars.cars;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;

@Schema(
    name = "Car",
    description = "A catalog vehicle with rental charges expressed in Brazilian centavos.")
public record CarResponse(
    @Schema(description = "Identifier of this vehicle.", accessMode = Schema.AccessMode.READ_ONLY)
        UUID id,
    @Schema(description = "Model name.", example = "Argo", maxLength = 100) String name,
    @Schema(
            description = "Description of this vehicle.",
            example = "Five-door hatchback.",
            maxLength = 255)
        String description,
    @Schema(description = "Vehicle manufacturer.", example = "Fiat", maxLength = 80) String brand,
    @Schema(
            description = "Unique, immutable uppercase license plate.",
            example = "ABC1D23",
            maxLength = 20)
        String licensePlate,
    @Schema(description = "Whether this car can be rented.") boolean available,
    @Schema(
            description = "Daily rental price in Brazilian centavos.",
            minimum = "0",
            example = "15050")
        int dailyRateCents,
    @Schema(description = "Fine in Brazilian centavos.", minimum = "0", example = "0")
        int fineAmountCents,
    @Schema(description = "Identifier of the assigned body style.") UUID categoryId,
    @Schema(description = "Identifiers of the features assigned to this vehicle.")
        Set<UUID> specificationIds) {

  public CarResponse {
    specificationIds = Set.copyOf(specificationIds);
  }
}
