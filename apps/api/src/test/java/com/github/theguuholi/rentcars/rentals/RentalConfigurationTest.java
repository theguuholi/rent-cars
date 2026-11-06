package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RentalConfigurationTest {
  @Nested
  class RentalClock {
    @Test
    void givenProductionConfigurationWhenCreatingClockThenUsesCurrentUtcTime() {
      var before = Instant.now();
      var clock = new RentalConfiguration().rentalClock();
      assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
      assertThat(clock.instant()).isBetween(before, Instant.now());
    }
  }
}
