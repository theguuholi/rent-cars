package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

@Schema(
    description =
        "Car attributes. On PATCH, omitted fields retain their values. All supplied values must be non-null.")
public record CarRequest(
    @Schema(description = "Model name.", maxLength = 100, example = "Argo") String name,
    @Schema(description = "Description of this vehicle.", maxLength = 255) String description,
    @Schema(description = "Vehicle manufacturer.", maxLength = 80, example = "Fiat") String brand,
    @Schema(
            description = "Unique plate, trimmed and uppercased before validation.",
            maxLength = 20,
            example = "ABC1D23")
        String licensePlate,
    @Schema(description = "Whether this car can be rented.") Boolean available,
    @Schema(description = "Daily rental price in Brazilian centavos.", example = "15050") @Min(0)
        Integer dailyRateCents,
    @Schema(description = "Fine in Brazilian centavos.", example = "0") @Min(0)
        Integer fineAmountCents,
    @Schema(description = "Identifier of the car's body style.") UUID categoryId,
    @Schema(
            description =
                "Full selection of feature identifiers. An empty array clears the selection.")
        Set<UUID> specificationIds) {

  public CarRequest {
    if (specificationIds != null) specificationIds = Set.copyOf(specificationIds);
  }

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

  // Rental prices are integer cents. Reject coercions (e.g. 1.99 to 1) for this contract.
  private static final ObjectReader READER =
      JsonMapper.builder()
          .changeDefaultNullHandling(ignored -> JsonSetter.Value.construct(Nulls.SET, Nulls.FAIL))
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
          .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
          .withCoercionConfig(
              LogicalType.Textual,
              config -> {
                config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
              })
          .build()
          .readerFor(CarRequest.class);

  static CarRequest parse(JsonNode body) {
    if (!body.isObject())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a car object");
    // A missing record component means “keep the existing value” on PATCH.
    if (body.valueStream().anyMatch(JsonNode::isNull))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Car attributes cannot be null");
    try {
      return READER.readValue(body);
    } catch (JacksonException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid car attributes", e);
    }
  }
}
