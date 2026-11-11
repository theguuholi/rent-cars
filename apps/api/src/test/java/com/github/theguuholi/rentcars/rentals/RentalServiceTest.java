package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.theguuholi.rentcars.cars.Car;
import com.github.theguuholi.rentcars.cars.CarRentalAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RentalServiceTest {
  final RentalRepository repository = mock();
  final CarRentalAccess cars = mock();
  final Car car = mock();
  final UUID carId = UUID.randomUUID(), accountId = UUID.randomUUID(), rentalId = UUID.randomUUID();
  final Instant now = Instant.parse("2026-11-06T15:00:00.123456789Z");
  final RentalService service =
      new RentalService(repository, cars, Clock.fixed(now, ZoneOffset.UTC));

  @Nested
  class Create {
    @Test
    void givenAvailableCarWhenCreatingThenRentsAndPersistsAtDatabasePrecision() {
      when(cars.getForRental(carId)).thenReturn(car);
      when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
      var rental = service.create(accountId, carId, now.plusSeconds(86400));
      verify(car).rent();
      assertThat(rental.startDate()).isEqualTo(Instant.parse("2026-11-06T15:00:00.123456Z"));
      assertThat(rental.expectedReturnDate()).isEqualTo(rental.startDate().plusSeconds(86400));
    }

    @Test
    void givenExistingOpenRentalWhenCreatingThenDoesNotRentOrSave() {
      when(cars.getForRental(carId)).thenReturn(car);
      when(repository.existsByAccountIdAndEndDateIsNull(accountId)).thenReturn(true);
      var expectedReturn = now.plusSeconds(86400);
      assertThatThrownBy(() -> service.create(accountId, carId, expectedReturn))
          .isInstanceOf(RentalException.class);
      verify(car, never()).rent();
      verify(repository, never()).saveAndFlush(any());
    }
  }

  @Nested
  class ListRentals {
    @Test
    void givenOwnerWhenListingThenQueriesOnlyTheirRentals() {
      var rows = List.of(Rental.start(accountId, car, now, now.plusSeconds(86400)));
      when(repository.findByAccountIdOrderByStartDateDescIdDesc(accountId)).thenReturn(rows);
      assertThat(service.list(accountId)).isSameAs(rows);
    }
  }

  @Nested
  class ReturnCar {
    @Test
    void givenOpenRentalWhenReturningThenUsesCurrentCarPricesAndReleasesIt() {
      when(car.id()).thenReturn(carId);
      var exactNow = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      var rental =
          Rental.start(accountId, car, exactNow.minusSeconds(259200), exactNow.minusSeconds(86400));
      when(repository.findForReturn(rentalId, accountId)).thenReturn(Optional.of(rental));
      when(cars.getForRental(carId)).thenReturn(car);
      when(car.rentalDailyRateCents()).thenReturn(100);
      when(car.rentalFineAmountCents()).thenReturn(30);
      when(repository.saveAndFlush(rental)).thenReturn(rental);
      assertThat(service.returnCar(rentalId, accountId)).isSameAs(rental);
      assertThat(rental).extracting("totalCents").isEqualTo(330L);
      verify(car).releaseAfterReturn();
    }

    @Test
    void givenMissingOrForeignRentalWhenReturningThenReportsNotFound() {
      assertThatThrownBy(() -> service.returnCar(rentalId, accountId))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
      verifyNoInteractions(cars);
    }

    @Test
    void givenAlreadyReturnedRentalWhenReturningThenDoesNotReleaseVehicle() {
      var rental = Rental.start(accountId, car, now, now.plusSeconds(86400));
      rental.finish(now, 100);
      when(repository.findForReturn(rentalId, accountId)).thenReturn(Optional.of(rental));
      assertThatThrownBy(() -> service.returnCar(rentalId, accountId))
          .isInstanceOf(RentalException.class);
      verifyNoInteractions(cars);
      verify(car, never()).releaseAfterReturn();
    }
  }
}
