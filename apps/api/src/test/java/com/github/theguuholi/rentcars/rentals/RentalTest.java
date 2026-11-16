package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.github.theguuholi.rentcars.cars.Car;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RentalTest {
  final Instant now = Instant.parse("2026-11-06T15:00:00Z");
  final UUID owner = UUID.randomUUID();
  final Car car = mock();

  Rental open() {
    return Rental.start(owner, car, now, now.plus(Duration.ofDays(2)));
  }

  @Nested
  class Start {
    @Test
    void givenExactly24HoursWhenStartingThenCreatesOpenRental() {
      var rental = Rental.start(owner, car, now, now.plus(Duration.ofHours(24)));
      assertThat(rental)
          .extracting(
              "accountId", "car", "startDate", "expectedReturnDate", "endDate", "totalCents")
          .containsExactly(owner, car, now, now.plus(Duration.ofHours(24)), null, null);
    }

    @Test
    void givenCarPricesWhenStartingThenSnapshotsPrices() {
      org.mockito.Mockito.when(car.rentalDailyRateCents()).thenReturn(15000);
      org.mockito.Mockito.when(car.rentalFineAmountCents()).thenReturn(3000);
      var rental = open();
      assertThat(rental)
          .extracting("dailyRateCents", "fineAmountCents")
          .containsExactly(15000, 3000);
    }

    @Test
    void givenLessThan24HoursWhenStartingThenRejectsPeriod() {
      assertThatThrownBy(() -> Rental.start(owner, car, now, now.plusSeconds(86399)))
          .isInstanceOfSatisfying(
              RentalException.class,
              e -> assertThat(e.reason()).isEqualTo(RentalException.Reason.INVALID_PERIOD));
    }
  }

  @Nested
  class Finish {
    @Test
    void givenOpenRentalWhenFinishingThenSetsChargeAndDateTogether() {
      var rental = open();
      rental.finish(now, new RentalPricing.Charge(1, 0, 0));
      assertThat(rental)
          .extracting("endDate", "totalCents", "billedDays", "lateDays")
          .containsExactly(now, 0L, 1L, 0L);
    }

    @Test
    void givenReturnedRentalWhenFinishingAgainThenPreservesOriginalReturn() {
      var rental = open();
      rental.finish(now, new RentalPricing.Charge(1, 0, 100));
      var secondReturn = now.plusSeconds(1);
      assertThatThrownBy(() -> rental.finish(secondReturn, new RentalPricing.Charge(1, 0, 200)))
          .isInstanceOf(RentalException.class);
      assertThat(rental).extracting("endDate", "totalCents").containsExactly(now, 100L);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,0,100", "1,-1,100"})
    void givenInvalidDayCountsWhenFinishingThenKeepsRentalOpen(
        long days, long lateDays, long amount) {
      var rental = open();
      assertThatThrownBy(() -> rental.finish(now, new RentalPricing.Charge(days, lateDays, amount)))
          .isInstanceOf(RentalException.class);
      assertThat(rental).extracting("endDate", "totalCents").containsExactly(null, null);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void givenInvalidTimeOrChargeWhenFinishingThenKeepsRentalOpen(long amount) {
      var rental = open();
      var returnedAt = amount == 0 ? now.minusSeconds(1) : now;
      assertThatThrownBy(() -> rental.finish(returnedAt, new RentalPricing.Charge(1, 0, amount)))
          .isInstanceOf(RentalException.class);
      assertThat(rental).extracting("endDate", "totalCents").containsExactly(null, null);
    }
  }

  @Nested
  class RequireOpen {
    @Test
    void givenOpenRentalWhenCheckingThenAllowsReturn() {
      assertThatCode(() -> open().requireOpen()).doesNotThrowAnyException();
    }

    @Test
    void givenReturnedRentalWhenCheckingThenRejectsReturn() {
      var rental = open();
      rental.finish(now, new RentalPricing.Charge(1, 0, 100));
      assertThatThrownBy(rental::requireOpen)
          .isInstanceOfSatisfying(
              RentalException.class,
              e -> assertThat(e.reason()).isEqualTo(RentalException.Reason.ALREADY_RETURNED));
    }
  }
}
