package com.github.theguuholi.rentcars.cars;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;

@Schema(implementation = Car.class)
public record CarResponse(
    UUID id,
    String name,
    String description,
    String brand,
    String licensePlate,
    boolean available,
    int dailyRateCents,
    int fineAmountCents,
    UUID categoryId,
    Set<UUID> specificationIds) {

  public CarResponse {
    specificationIds = Set.copyOf(specificationIds);
  }
}
