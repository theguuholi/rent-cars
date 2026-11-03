package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonValue;

/** An exact, non-negative rental charge in Brazilian centavos. */
public record BrlAmount(@JsonValue int cents) {
  public BrlAmount {
    if (cents < 0) throw new IllegalArgumentException("A BRL amount cannot be negative");
  }
}
