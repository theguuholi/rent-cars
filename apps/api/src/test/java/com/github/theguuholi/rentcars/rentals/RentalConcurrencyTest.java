package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;

import com.github.theguuholi.rentcars.cars.CarUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class RentalConcurrencyTest extends RentalIntegrationSupport {
  @Autowired TransactionTemplate transactions;

  @Nested
  class Create {
    @Test
    @DisplayName(
        "Given two drivers and one car, when both rent at once, then only one rental commits")
    void twoDriversCompeteForOneCar() throws Exception {
      // Given: both drivers want the same available car.
      Runnable firstDriverRents = () -> service.create(owner, carId, NOW.plusSeconds(86400));
      Runnable secondDriverRents = () -> service.create(other, carId, NOW.plusSeconds(86400));

      // When: both transactions begin the rental operation together.
      assertOneCommitAndOneConflict(firstDriverRents, secondDriverRents);

      // Then: exactly one open rental exists and the car is unavailable.
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from rentals where end_date is null", Integer.class))
          .isEqualTo(1);
      assertThat(available(carId)).isFalse();
    }

    @Test
    @DisplayName(
        "Given one driver and two cars, when both rents start at once, then only one commits")
    void oneDriverCompetesForTwoCars() throws Exception {
      // Given: each transaction locks a different available car for the same driver.
      UUID secondCarId = car();
      Runnable driverRentsFirstCar = () -> service.create(owner, carId, NOW.plusSeconds(86400));
      Runnable driverRentsSecondCar =
          () -> service.create(owner, secondCarId, NOW.plusSeconds(86400));

      // When: the driver's two transactions begin together.
      assertOneCommitAndOneConflict(driverRentsFirstCar, driverRentsSecondCar);

      // Then: the account has one open rental and only that car is unavailable.
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from rentals where account_id=? and end_date is null",
                  Integer.class,
                  owner))
          .isEqualTo(1);
      assertThat(available(carId) ^ available(secondCarId)).isTrue();
    }
  }

  @Nested
  class ReturnCar {
    @Test
    @DisplayName(
        "Given a pending price edit, when returning the car, then return waits and uses the committed price")
    void returnWaitsForCarPriceEdit() throws Exception {
      // Given: a rental is open and a transaction changes its car's daily rate.
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        var pendingReturn =
            transactions.execute(
                status -> {
                  jdbc.update(
                      "update cars set daily_rate_cents=20000,version=version+1 where id=?", carId);
                  int priceEditorPid =
                      jdbc.queryForObject("select pg_backend_pid()", Integer.class);

                  // When: return tries to lock the car before the price edit commits.
                  var returning = workers.submit(() -> service.returnCar(rental.id(), owner));
                  awaitTransactionBlockedBy(priceEditorPid);
                  return returning; // The price transaction commits before the future is read.
                });

        // Then: return uses the committed price rather than the previous rate.
        assertThat(pendingReturn.get(15, TimeUnit.SECONDS))
            .extracting("totalCents")
            .isEqualTo(20000L);
      }
    }

    @Test
    @DisplayName("Given one open rental, when two returns race, then only one finishes it")
    void twoReturnsCompeteForOneRental() throws Exception {
      // Given: one open rental belongs to the driver.
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      Runnable firstReturn = () -> service.returnCar(rental.id(), owner);
      Runnable secondReturn = () -> service.returnCar(rental.id(), owner);

      // When: both returns begin together.
      assertOneCommitAndOneConflict(firstReturn, secondReturn);

      // Then: the car is available and the rental was completed only once.
      assertThat(available(carId)).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "select total_cents from rentals where id=?", Long.class, rental.id()))
          .isEqualTo(10000L);
      assertThat(jdbc.queryForObject("select version from cars where id=?", Long.class, carId))
          .isEqualTo(2);
    }
  }

  private void awaitTransactionBlockedBy(int blockingPid) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                jdbc.queryForObject(
                    "select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                    Boolean.class,
                    blockingPid));
  }

  private void assertOneCommitAndOneConflict(Runnable first, Runnable second) throws Exception {
    var startingLine = new CyclicBarrier(2);
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var firstOutcome = workers.submit(() -> runTransaction(first, startingLine));
      var secondOutcome = workers.submit(() -> runTransaction(second, startingLine));

      assertThat(
              List.of(
                  firstOutcome.get(20, TimeUnit.SECONDS), secondOutcome.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(Outcome.COMMITTED, Outcome.CONFLICT);
    }
  }

  private Outcome runTransaction(Runnable operation, CyclicBarrier startingLine) {
    try {
      transactions.executeWithoutResult(
          status -> {
            try {
              startingLine.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
              throw new IllegalStateException("Competing transaction did not start", e);
            }
            operation.run();
          });
      return Outcome.COMMITTED;
    } catch (RentalException | DataIntegrityViolationException | CarUnavailableException e) {
      return Outcome.CONFLICT;
    }
  }

  private enum Outcome {
    COMMITTED,
    CONFLICT
  }
}
