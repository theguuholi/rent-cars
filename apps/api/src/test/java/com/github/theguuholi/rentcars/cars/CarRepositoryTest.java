package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CarRepositoryTest {
  @Autowired CarRepository cars;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;

  private UUID sedan(String plate) {
    return jdbc.queryForObject(
        """
        insert into cars (name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
        select 'Test','Sedan','Honda',?,id,100,0 from categories where name='SEDAN' returning id
        """,
        UUID.class,
        plate);
  }

  @Nested
  class FindWithCategory {
    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName(
        "Given a sedan, when fetching its category with the car, then its own ID and category name are returned")
    void selectedCategory() {
      var id = sedan("CAT1234");
      var row = cars.findWithCategory(id).orElseThrow();
      assertThat(row.car().id()).isEqualTo(id);
      assertThat(row.categoryName()).isEqualTo("SEDAN");
      assertThat(cars.findWithCategory(UUID.randomUUID())).isEmpty();
    }
  }

  @Nested
  class FindAssignedSpecifications {
    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName(
        "Given two cars and a feature assigned to only one, when looking up specifications, then only that car returns its ID and name")
    void separatesCars() {
      var first = sedan("EQP1234");
      var second = sedan("EQP1235");
      var feature =
          jdbc.queryForObject(
              "insert into specifications(name,description) values ('TEST CAMERA','Rear view') returning id",
              UUID.class);
      jdbc.update(
          "insert into cars_specifications(car_id,specification_id) values (?,?)", first, feature);
      assertThat(cars.findAssignedSpecifications(first))
          .containsExactly(new CarDetail.AssignedSpecification(feature, "TEST CAMERA"));
      assertThat(cars.findAssignedSpecifications(second)).isEmpty();
    }
  }

  @Nested
  @DisplayName("findForUpdate")
  class FindForUpdate {
    @DisplayName(
        "Given an unknown ID, when requesting an update lock, then the query returns no car")
    @Test
    void givenUnknownIdWhenLockingThenReturnsEmpty() {
      var missing = tx.execute(status -> cars.findForUpdate(UUID.randomUUID()));
      assertThat(missing).isEmpty();
    }

    @DisplayName("Given a locked car, when a competing update starts, then it waits for commit")
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
