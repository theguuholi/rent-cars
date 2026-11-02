package com.github.theguuholi.rentcars.specifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "Send both fields when creating a specification. For PUT or PATCH, omit a field to keep its current value. Explicit nulls and blank strings are invalid.")
public class SpecificationRequest {
  // An absent field preserves the stored value on update; an explicit null fails @NotBlank.
  @JsonProperty
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  @Schema(
      description =
          "Unique feature name. The API converts it to uppercase before checking its length and uniqueness.",
      maxLength = 255,
      example = "gps")
  String name;

  @JsonProperty
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  @Schema(
      description = "What the feature provides for the driver.",
      example = "Turn-by-turn satellite navigation.")
  String description;
}
