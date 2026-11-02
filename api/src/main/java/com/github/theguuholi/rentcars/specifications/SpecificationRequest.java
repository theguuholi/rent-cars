package com.github.theguuholi.rentcars.specifications;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;

public record SpecificationRequest(@NotNull Attributes specification) {
  public static class Attributes {
    // An absent field preserves the stored value on update; an explicit null fails @NotBlank.
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    public String name;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    public String description;
  }
}
