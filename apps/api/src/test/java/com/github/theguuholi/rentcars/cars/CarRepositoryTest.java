package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CarRepositoryTest {
  @Autowired CarRepository cars;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;

  @Nested
  @DisplayName("Database constraints")
  class DatabaseConstraints {
    @DisplayName(
        "Given a car with specifications, deleting its category removes the car and links but keeps the specifications")
    @Test
    void givenCarWithSpecsWhenDeletingItsCategoryThenRemovesCarAndLinksOnly() {
      tx.executeWithoutResult(
          status -> {
            var category =
                jdbc.queryForObject(
                    "insert into categories (name) values ('CAR_TEST') returning id", UUID.class);
            var spec =
                jdbc.queryForObject(
                    "insert into specifications (name,description) values ('CAR_TEST','test') returning id",
                    UUID.class);
            var car =
                jdbc.queryForObject(
                    """
            insert into cars (name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
            values ('Sedan','Automatic','Honda','REF1234',?,1,0) returning id
            """,
                    UUID.class,
                    category);
            jdbc.update(
                "insert into cars_specifications(car_id,specification_id) values (?,?)", car, spec);
            jdbc.update("delete from categories where id=?", category);
            assertThat(cars.findById(car)).isEmpty();
            assertThat(
                    jdbc.queryForObject(
                        "select count(*) from cars_specifications where car_id=?",
                        Integer.class,
                        car))
                .isZero();
            assertThat(
                    jdbc.queryForObject(
                        "select count(*) from specifications where id=?", Integer.class, spec))
                .isOne();
            status.setRollbackOnly();
          });
    }

    private static final String INSERT =
        """
        insert into cars (name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
        select ?,?,?,?,id,?,? from categories where name='SEDAN'
        """;

    @DisplayName("Given an invalid plate or negative charge, PostgreSQL rejects a direct insert")
    @ParameterizedTest
    @CsvSource({"abc1234,0,1", "ABC1234,-1,1", "ABC1234,0,-1"})
    void givenInvalidChargeOrPlateWhenInsertingThenRejectsRow(String plate, int rate, int fine) {
      assertThatThrownBy(() -> jdbc.update(INSERT, "Test", "Sedan", "Honda", plate, rate, fine))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("Given text at its maximum length, PostgreSQL accepts a direct insert")
    @Test
    void givenMaximumLengthsWhenInsertingThenAcceptsRow() {
      tx.executeWithoutResult(
          status -> {
            assertThat(
                    jdbc.update(
                        INSERT,
                        "n".repeat(100),
                        "d".repeat(255),
                        "b".repeat(80),
                        "Z".repeat(20),
                        0,
                        0))
                .isOne();
            status.setRollbackOnly();
          });
    }

    @DisplayName("Given oversized text, PostgreSQL rejects a direct insert with a truncation error")
    @ParameterizedTest
    @CsvSource({"101,255,80,20", "100,256,80,20", "100,255,81,20", "100,255,80,21"})
    void givenOversizedTextWhenInsertingThenRejectsRow(
        int name, int description, int brand, int plate) {
      Object[] attributes = {
        "n".repeat(name), "d".repeat(description), "b".repeat(brand), "Z".repeat(plate), 0, 0
      };
      assertThatThrownBy(() -> jdbc.update(INSERT, attributes))
          .isInstanceOf(DataIntegrityViolationException.class)
          .rootCause()
          .isInstanceOfSatisfying(
              SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("22001"));
    }
  }

  @Nested
  @DisplayName("findForUpdate")
  class FindForUpdate {
    @DisplayName("Given an unknown ID, requesting an update lock returns no car")
    @Test
    void givenUnknownIdWhenLockingThenReturnsEmpty() {
      var missing = tx.execute(status -> cars.findForUpdate(UUID.randomUUID()));
      assertThat(missing).isEmpty();
    }

    @DisplayName("Given a locked car, a competing update waits for the lock and then commits")
    @Test
    void givenLockedCarWhenUpdatingConcurrentlyThenWaitsForCommit() throws Exception {
      var id =
          jdbc.queryForObject(
              """
        insert into cars (name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
        select 'Test','Sedan','Honda','LOCK123',id,100,0 from categories where name='SEDAN'
        returning id
        """,
              UUID.class);
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        Future<Integer> waiting =
            tx.execute(
                status -> {
                  assertThat(cars.findForUpdate(id)).isPresent();
                  int owner = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
                  var next =
                      workers.submit(
                          () -> jdbc.update("update cars set name='Updated' where id=?", id));
                  await()
                      .atMost(Duration.ofSeconds(10))
                      .until(
                          () ->
                              jdbc.queryForObject(
                                  "select exists (select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                                  Boolean.class,
                                  owner));
                  return next;
                });
        assertThat(waiting.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from cars where id=?", String.class, id))
            .isEqualTo("Updated");
      } finally {
        jdbc.update("delete from cars where id=?", id);
      }
    }
  }
}
