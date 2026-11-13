package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class RentalRepositoryTest extends RentalIntegrationSupport {
  @Autowired TransactionTemplate tx;

  @Nested
  class ExistsByAccountIdAndEndDateIsNull {
    @Test
    void givenRentalLifecycleWhenCheckingThenOnlyOpenRentalsCount() {
      assertThat(repository.existsByAccountIdAndEndDateIsNull(owner)).isFalse();
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      assertThat(repository.existsByAccountIdAndEndDateIsNull(owner)).isTrue();
      assertThat(repository.existsByAccountIdAndEndDateIsNull(other)).isFalse();
      service.returnCar(rental.id(), owner);
      assertThat(repository.existsByAccountIdAndEndDateIsNull(owner)).isFalse();
    }
  }

  @Nested
  class FindByAccountIdOrderByStartDateDescIdDesc {
    @Test
    void givenDifferentOwnersWhenListingThenLoadsOnlyOwnersCars() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      assertThat(repository.findByAccountIdOrderByStartDateDescIdDesc(other)).isEmpty();
      var rows = repository.findByAccountIdOrderByStartDateDescIdDesc(owner);
      assertThat(rows).extracting(Rental::id).containsExactly(rental.id());
      assertThat(rows.getFirst())
          .extracting("car")
          .satisfies(
              value ->
                  assertThat(
                          ((com.github.theguuholi.rentcars.cars.Car) value).rentalDailyRateCents())
                      .isEqualTo(10000));
    }
  }

  @Nested
  class FindForReturn {
    @Test
    void givenOwnerWhenLockingThenHidesForeignAndUnknownRentals() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      tx.executeWithoutResult(
          s -> {
            assertThat(repository.findForReturn(rental.id(), owner)).isPresent();
            assertThat(repository.findForReturn(rental.id(), other)).isEmpty();
            assertThat(repository.findForReturn(UUID.randomUUID(), owner)).isEmpty();
          });
    }
  }

  @Nested
  class DatabaseGuarantees {
    @Test
    void givenUnknownCarWhenInsertingRentalThenForeignKeyRejectsIt() {
      assertDatabaseViolation(
          "23503", "rentals_car_id_fkey", () -> insert(owner, UUID.randomUUID()));
      assertThat(jdbc.queryForObject("select count(*) from rentals", Integer.class)).isZero();
    }

    @Test
    void givenReturnBeforeOneDayWhenInsertingRentalThenPeriodCheckRejectsIt() {
      assertDatabaseViolation(
          "23514", "rentals_period", () -> insert(owner, carId, NOW.plusSeconds(86399)));
      assertThat(jdbc.queryForObject("select count(*) from rentals", Integer.class)).isZero();
    }

    @Test
    void givenMissingAccountWhenPersistingThenRollsBackCarAvailability() {
      var missingAccount = UUID.randomUUID();
      var expectedReturn = NOW.plusSeconds(86400);
      assertThatThrownBy(() -> service.create(missingAccount, carId, expectedReturn))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(available(carId)).isTrue();
      assertThat(jdbc.queryForObject("select count(*) from rentals", Integer.class)).isZero();
    }

    @Test
    void givenHistoryWhenDeletingCarOrOwnerDirectlyThenForeignKeysPreserveIt() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      service.returnCar(rental.id(), owner);
      assertThatThrownBy(() -> jdbc.update("delete from cars where id=?", carId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update("delete from accounts where id=?", owner))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givenOpenRentalWhenInsertingDuplicatesDirectlyThenBothUniqueIndexesProtectIt() {
      service.create(owner, carId, NOW.plusSeconds(86400));
      UUID second = car();
      assertDatabaseViolation("23505", "rentals_open_car", () -> insert(other, carId));
      assertDatabaseViolation("23505", "rentals_open_account", () -> insert(owner, second));
    }

    @Test
    void givenInconsistentReturnWhenWritingDirectlyThenCheckConstraintRejectsIt() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      var rentalId = rental.id();
      for (String update :
          List.of(
              "total_cents=0",
              "end_date=start_date",
              "end_date=start_date,total_cents=-1",
              "end_date=start_date-interval '1 second',total_cents=0",
              "expected_return_date=start_date+interval '23 hours'")) {
        assertThatThrownBy(
                () -> jdbc.update("update rentals set " + update + " where id=?", rentalId))
            .isInstanceOf(DataIntegrityViolationException.class);
      }
    }

    void insert(UUID account, UUID car) {
      insert(account, car, NOW.plusSeconds(86400));
    }

    void insert(UUID account, UUID car, java.time.Instant expectedReturn) {
      jdbc.update(
          "insert into rentals(car_id,account_id,start_date,expected_return_date) values(?,?,?::timestamptz,?::timestamptz)",
          car,
          account,
          NOW.toString(),
          expectedReturn.toString());
    }

    void assertDatabaseViolation(
        String state,
        String constraint,
        org.assertj.core.api.ThrowableAssert.ThrowingCallable write) {
      assertThatThrownBy(write)
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasRootCauseInstanceOf(PSQLException.class)
          .satisfies(
              error -> {
                var postgresError = (PSQLException) error.getCause();
                assertThat(postgresError.getSQLState()).isEqualTo(state);
                assertThat(postgresError.getServerErrorMessage().getConstraint())
                    .isEqualTo(constraint);
              });
    }
  }

  @Nested
  class ConcurrentWrites {
    @Test
    void givenConcurrentPriceEditWhenReturningThenReadsPricesAfterAcquiringCarLock()
        throws Exception {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        var returning =
            tx.execute(
                status -> {
                  jdbc.update(
                      "update cars set daily_rate_cents=20000,version=version+1 where id=?", carId);
                  int blocker = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
                  var future = workers.submit(() -> service.returnCar(rental.id(), owner));
                  org.awaitility.Awaitility.await()
                      .atMost(java.time.Duration.ofSeconds(10))
                      .until(
                          () ->
                              jdbc.queryForObject(
                                  "select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                                  Boolean.class,
                                  blocker));
                  return future;
                });
        assertThat(returning.get(15, TimeUnit.SECONDS)).extracting("totalCents").isEqualTo(20000L);
      }
    }

    @Test
    void givenTwoDriversWhenRentingSameCarConcurrentlyThenOnlyOneCommits() throws Exception {
      compete(
          () -> service.create(owner, carId, NOW.plusSeconds(86400)),
          () -> service.create(other, carId, NOW.plusSeconds(86400)));
      assertThat(available(carId)).isFalse();
      assertThat(jdbc.queryForObject("select count(*) from rentals", Integer.class)).isEqualTo(1);
    }

    @Test
    void givenOneDriverWhenRentingTwoCarsConcurrentlyThenOnlyOneCommits() throws Exception {
      UUID second = car();
      compete(
          () -> service.create(owner, carId, NOW.plusSeconds(86400)),
          () -> service.create(owner, second, NOW.plusSeconds(86400)));
      assertThat(jdbc.queryForObject("select count(*) from rentals", Integer.class)).isEqualTo(1);
      assertThat(available(carId) ^ available(second)).isTrue();
    }

    @Test
    void givenOneRentalWhenReturningConcurrentlyThenOnlyOneReturnCommits() throws Exception {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      compete(
          () -> service.returnCar(rental.id(), owner), () -> service.returnCar(rental.id(), owner));
      assertThat(available(carId)).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "select total_cents from rentals where id=?", Long.class, rental.id()))
          .isEqualTo(10000);
      assertThat(jdbc.queryForObject("select version from cars where id=?", Long.class, carId))
          .isEqualTo(2);
    }

    void compete(Runnable first, Runnable second) throws Exception {
      var gate = new CyclicBarrier(2);
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        var results =
            List.of(first, second).stream()
                .map(
                    action ->
                        workers.submit(
                            () -> {
                              try {
                                tx.executeWithoutResult(
                                    s -> {
                                      try {
                                        gate.await(10, TimeUnit.SECONDS);
                                      } catch (Exception e) {
                                        throw new IllegalStateException(e);
                                      }
                                      action.run();
                                    });
                                return "committed";
                              } catch (RentalException
                                  | DataIntegrityViolationException
                                  | com.github.theguuholi.rentcars.cars.CarUnavailableException _) {
                                return "conflict";
                              }
                            }))
                .toList();
        assertThat(
                List.of(
                    results.get(0).get(20, TimeUnit.SECONDS),
                    results.get(1).get(20, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder("committed", "conflict");
      }
    }
  }
}
