package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CarRentalAccessTest {
  final CarRepository cars = mock();
  final CarRentalAccess access = new CarRentalAccess(cars);

  @Nested
  class GetForRental {
    @Test
    void givenCarWhenLoadingThenReturnsLockedEntity() {
      UUID id = UUID.randomUUID();
      Car car = new Car();
      when(cars.findForUpdate(id)).thenReturn(Optional.of(car));
      assertThat(access.getForRental(id)).isSameAs(car);
    }

    @Test
    void givenUnknownCarWhenLoadingThenReportsNotFound() {
      assertThatThrownBy(() -> access.getForRental(UUID.randomUUID()))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
  }
}
