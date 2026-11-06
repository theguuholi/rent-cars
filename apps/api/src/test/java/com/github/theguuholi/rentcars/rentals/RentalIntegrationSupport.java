package com.github.theguuholi.rentcars.rentals;

import static org.mockito.Mockito.when;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
abstract class RentalIntegrationSupport {
  static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
  @Autowired JdbcTemplate jdbc;
  @Autowired RentalService service;
  @Autowired RentalRepository repository;
  @MockitoBean Clock clock;
  UUID owner, other, carId;

  @BeforeEach
  void fixtures() {
    when(clock.instant()).thenReturn(NOW);
    owner = account();
    other = account();
    carId = car();
  }

  UUID account() {
    return jdbc.queryForObject(
        "insert into accounts(email,password_hash) values (?, 'unused') returning id",
        UUID.class,
        "rental-" + UUID.randomUUID() + "@example.com");
  }

  UUID car() {
    return jdbc.queryForObject(
        """
        insert into cars(name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
        select 'Rental test','Rental test','Fiat',?,id,10000,3000 from categories where name='SEDAN' returning id
        """,
        UUID.class,
        UUID.randomUUID().toString().substring(0, 20).toUpperCase(java.util.Locale.ROOT));
  }

  boolean available(UUID id) {
    return jdbc.queryForObject("select available from cars where id=?", Boolean.class, id);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("delete from rentals");
    jdbc.update("delete from cars where name='Rental test'");
    jdbc.update("delete from accounts where email like 'rental-%'");
  }
}
