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
public class CarRequest {
  public String name;
  public String description;
  public String brand;
  public String licensePlate;
  public Boolean available;

  @Min(0)
  public Integer dailyRateCents;

  @Min(0)
  public Integer fineAmountCents;

  public UUID categoryId;
  public Set<UUID> specificationIds;
  // Rental prices are integer cents. Reject coercions (e.g. 1.99 to 1) for this contract.
  private static final ObjectReader READER =
      JsonMapper.builder()
          .changeDefaultNullHandling(ignored -> JsonSetter.Value.construct(Nulls.FAIL, Nulls.FAIL))
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
    try {
      return READER.readValue(body);
    } catch (JacksonException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid car attributes", e);
    }
  }
}
