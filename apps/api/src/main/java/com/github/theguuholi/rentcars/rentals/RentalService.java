package com.github.theguuholi.rentcars.rentals;

import com.github.theguuholi.rentcars.cars.Car;
import com.github.theguuholi.rentcars.cars.CarRentalAccess;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class RentalService {
  private final RentalRepository rentals;
  private final CarRentalAccess cars;
  private final Clock clock;

  RentalService(RentalRepository rentals, CarRentalAccess cars, Clock clock) {
    this.rentals = rentals;
    this.cars = cars;
    this.clock = clock;
  }

  @Transactional
  @Observed(name = "rental.start", contextualName = "rental.start")
  Rental create(UUID accountId, UUID carId, Instant expectedReturnDate) {
    // PostgreSQL stores microseconds. Use that same precision at the business boundary.
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Car car = cars.getForRental(carId);
    Rental rental =
        Rental.start(accountId, car, now, expectedReturnDate.truncatedTo(ChronoUnit.MICROS));
    if (rentals.existsByAccountIdAndEndDateIsNull(accountId)) {
      throw new RentalException(
          RentalException.Reason.ALREADY_RENTING, "Account already has an open rental");
    }
    car.rent();
    return rentals.saveAndFlush(rental);
  }

  List<Rental> list(UUID accountId) {
    return rentals.findByAccountIdOrderByStartDateDescIdDesc(accountId);
  }

  @Transactional
  @Observed(name = "rental.return", contextualName = "rental.return")
  Rental returnCar(UUID id, UUID accountId) {
    Rental rental =
        rentals
            .findForReturn(id, accountId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rental not found"));
    rental.requireOpen();
    Car car = cars.getForRental(rental.carId());
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var charge =
        RentalPricing.calculate(
            rental.startDate(),
            rental.expectedReturnDate(),
            now,
            rental.dailyRateCents(),
            rental.fineAmountCents());
    rental.finish(now, charge);
    car.releaseAfterReturn();
    return rentals.saveAndFlush(rental);
  }
}
