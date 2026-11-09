package com.github.theguuholi.rentcars.cars;

/** A vehicle cannot start another rental while unavailable. */
public final class CarUnavailableException extends RuntimeException {
  public CarUnavailableException() {
    super("Car is unavailable");
  }
}
