package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
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
          .satisfies(value ->
              assertThat(((com.github.theguuholi.rentcars.cars.Car) value).rentalDailyRateCents())
                  .isEqualTo(10000));
    }
  }

  @Nested
  class FindForReturn {
    @Test
    void givenOwnerWhenLockingThenHidesForeignAndUnknownRentals() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      tx.executeWithoutResult(s -> {
        assertThat(repository.findForReturn(rental.id(), owner)).isPresent();
        assertThat(repository.findForReturn(rental.id(), other)).isEmpty();
        assertThat(repository.findForReturn(UUID.randomUUID(), owner)).isEmpty();
      });
    }
  }

  @Nested
  class Atomicity {
    @Test
    void givenBothWritesFlushedWhenCommitFailsThenRentalAvailabilityAndVersionRollBack() {
      var expectedReturn = NOW.plusSeconds(86400);
      var version = jdbc.queryForObject("select version from cars where id=?", Long.class, carId);
      // This trigger exists only in the disposable Testcontainers database.
      jdbc.execute(
          """
          CREATE FUNCTION fail_rental_commit() RETURNS trigger LANGUAGE plpgsql AS $$
          BEGIN
            IF NOT EXISTS (SELECT 1 FROM rentals WHERE id = NEW.id)
               OR NOT EXISTS (SELECT 1 FROM cars WHERE id = NEW.car_id AND available = false) THEN
              RAISE EXCEPTION 'Expected both writes before the simulated failure' USING ERRCODE = 'P0002';
            END IF;
            RAISE EXCEPTION 'Simulated failure after both writes' USING ERRCODE = 'P0001';
          END;
          $$
          """);
      try {
        jdbc.execute(
            """
            CREATE CONSTRAINT TRIGGER fail_rental_commit
            AFTER INSERT ON rentals DEFERRABLE INITIALLY DEFERRED
            FOR EACH ROW EXECUTE FUNCTION fail_rental_commit()
            """);
        assertThatThrownBy(() -> service.create(owner, carId, expectedReturn))
            .hasRootCauseInstanceOf(PSQLException.class)
            .satisfies(error -> {
              var postgres = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
              assertThat(postgres.getSQLState()).isEqualTo("P0001");
              assertThat(postgres.getMessage()).contains("Simulated failure after both writes");
            });
      } finally {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_rental_commit ON rentals");
        jdbc.execute("DROP FUNCTION fail_rental_commit()");
      }
      // Outside the service transaction: these reads observe committed database state.
      assertThat(repository.findByAccountIdOrderByStartDateDescIdDesc(owner)).isEmpty();
      assertThat(available(carId)).isTrue();
      assertThat(jdbc.queryForObject("select version from cars where id=?", Long.class, carId))
          .isEqualTo(version);

      var saved = service.create(owner, carId, expectedReturn);
      assertThat(repository.findByAccountIdOrderByStartDateDescIdDesc(owner))
          .extracting(Rental::id)
          .containsExactly(saved.id());
      assertThat(available(carId)).isFalse();
    }

    @Test
    void givenFlushedWritesWhenRuntimeFailureEscapesThenOtherConnectionsNeverSeePartialState() {
      var expectedReturn = NOW.plusSeconds(86400);
      assertThatThrownBy(() ->
              tx.executeWithoutResult(status -> {
                var saved = service.create(owner, carId, expectedReturn);
                assertThat(available(carId)).isFalse();
                assertThat(repository.existsById(saved.id())).isTrue();
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                  var observed =
                      CompletableFuture.runAsync(
                          () -> {
                            assertThat(available(carId)).isTrue();
                            assertThat(
                                    jdbc.queryForObject(
                                        "select count(*) from rentals where id=?",
                                        Integer.class,
                                        saved.id()))
                                .isZero();
                          },
                          executor);
                  observed.orTimeout(5, TimeUnit.SECONDS).join();
                }
                throw new IllegalStateException("Simulated application failure after flush");
              }))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Simulated application failure after flush");
      assertThat(available(carId)).isTrue();
      assertThat(repository.findByAccountIdOrderByStartDateDescIdDesc(owner)).isEmpty();
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
    void givenOpenRentalWhenReadingAfterCommitThenItsAgreedPricesAreStored() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      assertThat(
              jdbc.queryForObject(
                  "select daily_rate_cents from rentals where id=?", Integer.class, rental.id()))
          .isEqualTo(10000);
      assertThat(
              jdbc.queryForObject(
                  "select fine_amount_cents from rentals where id=?", Integer.class, rental.id()))
          .isEqualTo(3000);
    }

    @Test
    void givenLegacyReceiptWithoutPriceDetailsWhenReadingThenItsRecordedTotalRemains() {
      var id = UUID.randomUUID();
      jdbc.update(
          "insert into rentals(id,car_id,account_id,start_date,expected_return_date,end_date,total_cents) values(?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?)",
          id,
          carId,
          owner,
          NOW.toString(),
          NOW.plusSeconds(86400).toString(),
          NOW.plusSeconds(86400).toString(),
          10000);
      assertThat(repository.findByAccountIdOrderByStartDateDescIdDesc(owner))
          .singleElement()
          .extracting("totalCents", "dailyRateCents", "fineAmountCents")
          .containsExactly(10000L, null, null);
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
          .satisfies(error -> {
            var postgresError = (PSQLException) error.getCause();
            assertThat(postgresError.getSQLState()).isEqualTo(state);
            assertThat(postgresError.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
          });
    }
  }
}
