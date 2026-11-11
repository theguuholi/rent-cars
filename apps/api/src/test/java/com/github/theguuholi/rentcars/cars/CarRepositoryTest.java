package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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

  @Autowired EntityManagerFactory entityManagerFactory;

  @Nested
  class Search {
    @Test
    @Transactional
    @DisplayName(
        "Given mixed availability and categories, when searching, then filtering precedes pagination")
    void filtersBeforePagination() {
      UUID first = sedan("SEARCH01");
      UUID second = sedan("SEARCH02");
      jdbc.update("update cars set name='Argo', brand='SearchBrand' where id=?", first);
      jdbc.update(
          "update cars set name='Bravo', brand='SearchBrand', available=false where id=?", second);
      var pageable = PageRequest.of(0, 1, Sort.by("name", "id"));
      var publicPage = cars.search(true, "%a%", "%searchbrand%", "%sedan%", pageable);
      assertThat(publicPage.getTotalElements()).isEqualTo(1);
      assertThat(publicPage.getContent()).extracting(Car::id).containsExactly(first);
      var adminPage = cars.search(false, "%", "%searchbrand%", "%sedan%", pageable);
      assertThat(adminPage.getTotalElements()).isEqualTo(2);
      assertThat(adminPage.getTotalPages()).isEqualTo(2);
      assertThat(adminPage.getContent()).extracting(Car::id).containsExactly(first);
      assertThat(cars.search(false, "%", "%searchbrand%", "%suv%", pageable).getTotalElements())
          .isZero();
      assertThat(cars.search(false, "%", "%searchbrand%", "%eda%", pageable).getTotalElements())
          .isEqualTo(2);
    }
  }

  @Nested
  class FindDetailedById {
    @Test
    @Transactional
    @DisplayName(
        "Given a stored car, when using the regular repository lookup, then the expanded catalog associations remain unloaded")
    void ordinaryLookupKeepsCatalogLazy() {
      var car = cars.findById(sedan("CAT1235")).orElseThrow();
      var persistence = entityManagerFactory.getPersistenceUnitUtil();
      assertThat(persistence.isLoaded(car, "category")).isFalse();
      assertThat(persistence.isLoaded(car, "specifications")).isFalse();
    }

    @Test
    @Transactional
    @DisplayName(
        "Given a persisted car, when fetching its detail graph, then its category, assigned specifications and IDs are already loaded")
    void loadsGraph() {
      var id = sedan("CAT1234");
      var car = cars.findDetailedById(id).orElseThrow();
      assertThat(car.id()).isEqualTo(id);
      var persistence = entityManagerFactory.getPersistenceUnitUtil();
      for (var attribute : List.of("category", "specifications", "specificationIds")) {
        assertThat(persistence.isLoaded(car, attribute)).as(attribute).isTrue();
      }
      assertThat(cars.findDetailedById(UUID.randomUUID())).isEmpty();
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

  private void rentalHistory(UUID carId, boolean returned) {
    UUID owner =
        jdbc.queryForObject(
            "insert into accounts(email,password_hash) values (?, 'unused') returning id",
            UUID.class,
            UUID.randomUUID() + "@example.com");
    jdbc.update(
        "insert into rentals(car_id,account_id,start_date,expected_return_date) values(?,?,now(),now()+interval '2 days')",
        carId,
        owner);
    if (returned)
      jdbc.update("update rentals set end_date=start_date,total_cents=100 where car_id=?", carId);
  }

  @Nested
  class HasOpenRental {
    @Test
    @Transactional
    @DisplayName(
        "Given open and closed history, when checking availability conflicts, then only an open rental counts")
    void givenRentalHistoryWhenCheckingThenOnlyOpenRentalCounts() {
      UUID id = sedan("RENTOPEN");
      assertThat(cars.hasOpenRental(id)).isFalse();
      rentalHistory(id, false);
      assertThat(cars.hasOpenRental(id)).isTrue();
      jdbc.update("update rentals set end_date=start_date,total_cents=100 where car_id=?", id);
      assertThat(cars.hasOpenRental(id)).isFalse();
    }
  }

  @Nested
  class HasRentalHistory {
    @Test
    @Transactional
    @DisplayName(
        "Given a returned rental, when checking deletion conflicts, then history still protects the car")
    void givenReturnedRentalWhenCheckingThenHistoryStillExists() {
      UUID id = sedan("RENTHISTORY");
      assertThat(cars.hasRentalHistory(id)).isFalse();
      rentalHistory(id, true);
      assertThat(cars.hasRentalHistory(id)).isTrue();
    }
  }
}
