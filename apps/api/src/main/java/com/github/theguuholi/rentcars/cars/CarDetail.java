package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(
    description =
        "Public rental vehicle details, including charges in Brazilian centavos and the names of its assigned catalog entries.")
public record CarDetail(
    @JsonUnwrapped Car car,
    @Schema(description = "Current name of the assigned vehicle category.", example = "SEDAN")
        String categoryName,
    @Schema(
            description =
                "Only the specifications assigned to this car, sorted by name. Empty when none are assigned.")
        List<AssignedSpecification> specifications) {

  @Schema(description = "Public identifier and current name of an assigned vehicle feature.")
  public record AssignedSpecification(
      @Schema(description = "Specification identifier.") UUID id,
      @Schema(description = "Catalog name of the specification.", example = "GPS") String name) {}
}
