package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class RentalPriceMigrationTest {
  @Test
  void
      givenExistingOpenAndReturnedRentalsWhenUpgradingThenOpenPriceIsCapturedAndOldTotalSurvives() {
    try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
      postgres.start();
      var dataSource =
          new DriverManagerDataSource(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      var migrations =
          Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
      migrations.target(MigrationVersion.fromVersion("12")).load().migrate();
      var jdbc = new JdbcTemplate(dataSource);
      UUID owner =
          jdbc.queryForObject(
              "insert into accounts(email,password_hash) values('migration@example.test','unused') returning id",
              UUID.class);
      UUID car =
          jdbc.queryForObject(
              """
              insert into cars(name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
              select 'Legacy','Legacy','Fiat','MIG1A23',id,12000,2500 from categories where name='SEDAN' returning id
              """,
              UUID.class);
      var start = Instant.parse("2026-11-06T15:00:00Z");
      UUID open = UUID.randomUUID();
      UUID returned = UUID.randomUUID();
      jdbc.update(
          "insert into rentals(id,car_id,account_id,start_date,expected_return_date) values(?,?,?,?::timestamptz,?::timestamptz)",
          open,
          car,
          owner,
          start.toString(),
          start.plusSeconds(86400).toString());
      jdbc.update(
          "insert into rentals(id,car_id,account_id,start_date,expected_return_date,end_date,total_cents) values(?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?)",
          returned,
          car,
          owner,
          start.minusSeconds(172800).toString(),
          start.minusSeconds(86400).toString(),
          start.toString(),
          15000);

      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      assertThat(
              jdbc.queryForObject(
                  "select daily_rate_cents from rentals where id=?", Integer.class, open))
          .isEqualTo(12000);
      assertThat(
              jdbc.queryForObject(
                  "select fine_amount_cents from rentals where id=?", Integer.class, open))
          .isEqualTo(2500);
      assertThat(
              jdbc.queryForObject(
                  "select daily_rate_cents from rentals where id=?", Integer.class, returned))
          .isNull();
      assertThat(
              jdbc.queryForObject(
                  "select total_cents from rentals where id=?", Long.class, returned))
          .isEqualTo(15000);
    }
  }
}
