package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RentalPricingTest {
  final Instant start = Instant.parse("2026-11-06T15:00:00Z");

  @Nested
  class Calculate {
    @ParameterizedTest
    @CsvSource({
      "0,100",
      "86399,100",
      "86400,100",
      "90000,100",
      "172799,100",
      "172800,200",
      "259199,200",
      "259200,330",
      "432000,590"
    })
    void givenElapsedTimeWhenCalculatingThenChargesCompletedDays(long seconds, long expected) {
      assertThat(
              RentalPricing.calculate(
                  start, start.plusSeconds(172800), start.plusSeconds(seconds), 100, 30))
          .isEqualTo(expected);
    }

    @Test
    void givenFreeVehicleWhenReturningThenChargesZero() {
      assertThat(
              RentalPricing.calculate(
                  start, start.plusSeconds(86400), start.plusSeconds(259200), 0, 0))
          .isZero();
    }

    @Test
    void givenLargeValidRatesWhenCalculatingThenUsesLongCentavos() {
      assertThat(
              RentalPricing.calculate(
                  start,
                  start.plusSeconds(86400),
                  start.plusSeconds(172800),
                  Integer.MAX_VALUE,
                  Integer.MAX_VALUE))
          .isEqualTo(3L * Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @CsvSource({"-1,100,30", "0,-1,30", "0,100,-1"})
    void givenInvalidInputsWhenCalculatingThenRejectsCharge(long seconds, int daily, int fine) {
      assertThatThrownBy(
              () ->
                  RentalPricing.calculate(
                      start, start.plusSeconds(86400), start.plusSeconds(seconds), daily, fine))
          .isInstanceOfSatisfying(
              RentalException.class,
              e -> assertThat(e.reason()).isEqualTo(RentalException.Reason.INVALID_RETURN));
    }

    @Test
    void givenOverflowWhenCalculatingThenRejectsInsteadOfWrapping() {
      var expectedReturn = Instant.MIN.plusSeconds(86400);
      assertThatThrownBy(
              () ->
                  RentalPricing.calculate(
                      Instant.MIN,
                      expectedReturn,
                      Instant.MAX,
                      Integer.MAX_VALUE,
                      Integer.MAX_VALUE))
          .isInstanceOf(RentalException.class)
          .hasMessageContaining("exceeds");
    }
  }
}
