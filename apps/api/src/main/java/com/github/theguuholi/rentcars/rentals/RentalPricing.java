package com.github.theguuholi.rentcars.rentals;

import java.time.Duration;
import java.time.Instant;

/** Matches the Elixir policy: completed 24-hour periods, at least one daily charge. */
final class RentalPricing {
  private RentalPricing() {}

  record Charge(long billedDays, long lateDays, long totalCents) {}

  static Charge calculate(
      Instant start,
      Instant expectedReturn,
      Instant returnedAt,
      int dailyRateCents,
      int fineAmountCents) {
    if (returnedAt.isBefore(start) || dailyRateCents < 0 || fineAmountCents < 0) {
      throw new RentalException(
          RentalException.Reason.INVALID_RETURN, "Invalid return time or charge");
    }
    long days = Math.max(1, Duration.between(start, returnedAt).toDays());
    long lateDays = Math.max(0, Duration.between(expectedReturn, returnedAt).toDays());
    try {
      return new Charge(
          days,
          lateDays,
          Math.addExact(
              Math.multiplyExact(days, dailyRateCents),
              Math.multiplyExact(lateDays, fineAmountCents)));
    } catch (ArithmeticException _) {
      throw new RentalException(
          RentalException.Reason.INVALID_RETURN, "Rental charge exceeds the supported amount");
    }
  }
}
