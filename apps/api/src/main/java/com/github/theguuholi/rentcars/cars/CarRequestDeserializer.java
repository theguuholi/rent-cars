package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

@JacksonComponent
final class CarRequestDeserializer extends ValueDeserializer<CarRequest> {
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

  @Override
  public CarRequest deserialize(JsonParser parser, DeserializationContext context) {
    JsonNode body = context.readTree(parser);
    if (!body.isObject())
      return context.reportInputMismatch(CarRequest.class, "Expected a car object");
    if (body.valueStream().anyMatch(JsonNode::isNull))
      return context.reportInputMismatch(CarRequest.class, "Car attributes cannot be null");
    return READER.readValue(body);
  }
}
