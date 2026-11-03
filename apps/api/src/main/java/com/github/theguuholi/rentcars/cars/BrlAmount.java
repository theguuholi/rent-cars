package com.github.theguuholi.rentcars.cars;

/** An exact, non-negative rental charge in Brazilian centavos. */
public record BrlAmount(int cents) {
  public BrlAmount {
    if (cents < 0) throw new IllegalArgumentException("A BRL amount cannot be negative");
  }
}
