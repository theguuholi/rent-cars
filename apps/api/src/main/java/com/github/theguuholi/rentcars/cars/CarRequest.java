package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.UUID;

@Schema(
    description =
        "Car attributes. On PATCH, omitted fields retain their values. All supplied values must be non-null.")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NON_PRIVATE)
public class CarRequest {
  @Schema(description = "Model name.", maxLength = 100, example = "Argo")
  String name;

  @Schema(description = "Description of this vehicle.", maxLength = 255)
  String description;

  @Schema(description = "Vehicle manufacturer.", maxLength = 80, example = "Fiat")
  String brand;

  @Schema(
      description = "Unique plate, trimmed and uppercased before validation.",
      maxLength = 20,
      example = "ABC1D23")
  String licensePlate;

  @Schema(description = "Whether this car can be rented.")
  Boolean available;

  @Schema(description = "Daily rental price in Brazilian centavos.", example = "15050")
  @Min(0)
  Integer dailyRateCents;

  @Schema(description = "Fine in Brazilian centavos.", example = "0")
  @Min(0)
  Integer fineAmountCents;

  @Schema(description = "Identifier of the car's body style.")
  UUID categoryId;

  @Schema(
      description = "Full selection of feature identifiers. An empty array clears the selection.")
  Set<UUID> specificationIds;

  @Schema(
      description =
          "On update, remove these image IDs belonging to the car. Omitted or empty preserves existing images; new multipart images are appended.")
  Set<UUID> removeImageIds;

  @Schema(
      description = "Required on update. Use the version returned when opening the editor.",
      minimum = "0")
  Long expectedVersion;

  @Schema(
      name = "CarUpdateRequest",
      allOf = CarRequest.class,
      requiredProperties = {"expectedVersion"})
  interface UpdateSchema {}

  @Schema(
      name = "CarCreateRequest",
      allOf = CarRequest.class,
      requiredProperties = {
        "name",
        "description",
        "brand",
        "licensePlate",
        "dailyRateCents",
        "fineAmountCents",
        "categoryId"
      })
  interface CreateSchema {}
}
