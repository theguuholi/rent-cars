package com.github.theguuholi.rentcars.rentals;

/** Business failures independent of HTTP and persistence. */
public final class RentalException extends RuntimeException {
  public enum Reason {
    INVALID_PERIOD,
    ALREADY_RENTING,
    ALREADY_RETURNED,
    INVALID_RETURN
  }

  private final Reason reason;

  public RentalException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
