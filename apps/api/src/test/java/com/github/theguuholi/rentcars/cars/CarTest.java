package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CarTest {
  @Test
  void givenAssignedSpecificationsWhenCallerChangesInputOrResponseThenCarKeepsItsSelection() {
    var car = new Car();
    var spec = UUID.randomUUID();
    var input = new CarRequest();
    input.specificationIds = new HashSet<>(Set.of(spec));
    car.apply(input);
    input.specificationIds.clear();

    assertThat(car.getSpecificationIds()).containsExactly(spec);
    assertThatThrownBy(() -> car.getSpecificationIds().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void givenUnspecifiedAvailabilityWhenApplyingPriceOnlyThenInitialAvailabilityIsPreserved() {
    var car = new Car();
    var price = new CarRequest();
    price.dailyRateCents = 3500;

    car.apply(price);
    assertThat(car.isAvailable()).isTrue();
    assertThat(car.getDailyRateCents()).isEqualTo(3500);
  }
}
