package com.github.theguuholi.rentcars.cars;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import com.github.theguuholi.rentcars.images.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class CarImageTestSupport {
  @TempDir static Path directory;

  @DynamicPropertySource
  static void directory(DynamicPropertyRegistry r) {
    r.add("rent.storage.directory", () -> directory.toString());
  }

  @Autowired CarRepository cars;
  @Autowired CarImageRepository images;
  @Autowired CarService service;
  @Autowired jakarta.validation.Validator validator;
  @Autowired ImageObjectRepository objects;
  @Autowired ImageStorage storage;
  @Autowired ImageCleanup cleanup;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcClient db;
  @Autowired MockMvc mvc;
  @Autowired JwtEncoder encoder;
  @Autowired ObjectMapper json;
  UUID car;

  @BeforeEach
  void createCar() {
    car =
        db.sql(
                "insert into cars(name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents) select 'Photo car','Test','Honda',?,id,100,0 from categories where name='SEDAN' returning id")
            .param(UUID.randomUUID().toString().substring(0, 18).toUpperCase())
            .query(UUID.class)
            .single();
  }

  org.springframework.mock.web.MockMultipartFile png() throws Exception {
    return new org.springframework.mock.web.MockMultipartFile(
        "image",
        "photo.png",
        "image/png",
        java.nio.file.Files.readAllBytes(Path.of("../../requests/fixtures/avatar.png")));
  }

  String token(String role) {
    var now = java.time.Instant.now();
    return "Bearer "
        + encoder
            .encode(
                JwtEncoderParameters.from(
                    JwtClaimsSet.builder()
                        .subject(UUID.randomUUID().toString())
                        .issuer("rent-cars")
                        .audience(List.of("rent-cars-api"))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(120))
                        .claim("roles", List.of(role))
                        .build()))
            .getTokenValue();
  }
}
