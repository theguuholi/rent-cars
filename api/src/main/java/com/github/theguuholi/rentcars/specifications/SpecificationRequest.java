package com.github.theguuholi.rentcars.specifications;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public class SpecificationRequest {
  // An absent field preserves the stored value on update; an explicit null fails @NotBlank.
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  String name;

  @JsonSetter(nulls = Nulls.AS_EMPTY)
  String description;
}
