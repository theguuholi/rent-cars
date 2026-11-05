package com.github.theguuholi.rentcars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.theguuholi.rentcars.shared.storage.ImageCleanup;
import com.github.theguuholi.rentcars.shared.storage.ImageStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  com.github.theguuholi.rentcars.auth.AdminRequests.class
})
class CarImageHttpTest {
  static final Path STORAGE;

  static {
    try {
      STORAGE = Files.createTempDirectory("car-images-http-");
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("rent.storage.local.directory", STORAGE::toString);
    registry.add("rent.storage.cleanup-delay-ms", () -> "3600000");
  }

  @MockitoSpyBean ImageStorage storage;
  @Autowired ImageCleanup cleanupJobs;
  @Autowired TransactionTemplate transactions;
  @Autowired JwtEncoder encoder;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @AfterEach
  void cleanup() {
    reset(storage);
    jdbc.update("delete from cars");
    jdbc.update("update image_cleanup_jobs set available_at = CURRENT_TIMESTAMP");
    cleanupJobs.run();
  }

  MockMultipartFile car() {
    String category = jdbc.queryForObject("select id::text from categories limit 1", String.class);
    String body =
        """
        {"name":"Image car","description":"Photo example","brand":"Fiat",
         "licensePlate":"%s","dailyRateCents":10000,"fineAmountCents":0,"categoryId":"%s"}
        """
            .formatted(UUID.randomUUID().toString().substring(0, 12), category);
    return new MockMultipartFile(
        "car", "", "application/json", body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static byte[] png() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  @Nested
  class Create {
    @Test
    @DisplayName(
        "Given a valid car and PNG, when posting multipart, then both are created and the image is readable")
    void createsWithImage() throws Exception {
      byte[] bytes = png();
      var response =
          mvc.perform(
                  multipart("/cars")
                      .file(car())
                      .file(
                          new MockMultipartFile("images", "../../photo.exe", "text/plain", bytes)))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse();
      String location = response.getHeader("Location");
      var detail =
          mvc.perform(get(location))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.images[0].contentType").value("image/png"))
              .andExpect(jsonPath("$.images[0].storageKey").doesNotExist())
              .andReturn()
              .getResponse();
      String id = json.readTree(detail.getContentAsString()).at("/images/0/id").asString();
      mvc.perform(get(location + "/images/" + id))
          .andExpect(status().isOk())
          .andExpect(content().contentType("image/png"))
          .andExpect(content().bytes(bytes));
      String key =
          jdbc.queryForObject(
              "select storage_key from cars_images where id = ?",
              String.class,
              UUID.fromString(id));
      assertThat(key).isEqualTo(location.substring(1) + "/" + id + ".png");
    }

    @Test
    @DisplayName(
        "Given no images, when posting multipart, then the car is created with an empty gallery")
    void noImages() throws Exception {
      var response =
          mvc.perform(multipart("/cars").file(car()))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse();
      mvc.perform(get(response.getHeader("Location"))).andExpect(jsonPath("$.images").isEmpty());
    }

    @Test
    @DisplayName(
        "Given fake image bytes, when creating a car, then validation rejects the whole request")
    void invalidImage() throws Exception {
      long before = jdbc.queryForObject("select count(*) from cars", Long.class);
      mvc.perform(
              multipart("/cars")
                  .file(car())
                  .file(
                      new MockMultipartFile("images", "x.jpg", "image/jpeg", new byte[] {1, 2, 3})))
          .andExpect(status().is(422));
      assertThat(jdbc.queryForObject("select count(*) from cars", Long.class)).isEqualTo(before);
    }
  }

  String uploaded() throws Exception {
    var result =
        mvc.perform(multipart("/cars").file(car()).file(new MockMultipartFile("images", png())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    String location = result.getHeader("Location");
    var detail = mvc.perform(get(location)).andReturn().getResponse();
    return location
        + "/images/"
        + json.readTree(detail.getContentAsString()).at("/images/0/id").asString();
  }

  @Nested
  class ReadImage {
    @Test
    @DisplayName(
        "Given a stored image, when reading without authentication, then its bytes are public but another car cannot address it")
    void publicAndOwnership() throws Exception {
      String url = uploaded();
      mvc.perform(
              get(url)
                  .with(
                      req -> {
                        req.removeHeader("Authorization");
                        return req;
                      }))
          .andExpect(status().isOk())
          .andExpect(content().bytes(png()));
      mvc.perform(get(url.replace(url.split("/")[2], UUID.randomUUID().toString())))
          .andExpect(status().isNotFound());
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      storage.delete(key);
      mvc.perform(get(url)).andExpect(status().isNotFound());
    }
  }

  @Nested
  class DeleteImage {
    @Test
    @DisplayName(
        "Given a stored image, when deleting, then metadata disappears and durable cleanup removes the file")
    void deletes() throws Exception {
      String url = uploaded();
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      mvc.perform(delete(url)).andExpect(status().isNoContent());
      mvc.perform(get(url)).andExpect(status().isNotFound());
      assertThat(Files.exists(STORAGE.resolve(key))).isTrue();
      cleanupJobs.run();
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
      mvc.perform(delete(url)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "Given an image owned by another car, when deleting, then the API returns 404 and preserves the image")
    void ownership() throws Exception {
      String url = uploaded();
      mvc.perform(delete(url.replace(url.split("/")[2], UUID.randomUUID().toString())))
          .andExpect(status().isNotFound());
      mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "Given anonymous or driver credentials, when uploading or deleting, then only administrators can mutate images")
    void authorization() throws Exception {
      String url = uploaded();
      mvc.perform(
              delete(url)
                  .with(
                      req -> {
                        req.removeHeader("Authorization");
                        return req;
                      }))
          .andExpect(status().isUnauthorized());
      mvc.perform(
              multipart("/cars")
                  .file(car())
                  .with(
                      req -> {
                        req.removeHeader("Authorization");
                        return req;
                      }))
          .andExpect(status().isUnauthorized());
      var now = Instant.now();
      String token =
          encoder
              .encode(
                  JwtEncoderParameters.from(
                      JwtClaimsSet.builder()
                          .issuer("rent-cars")
                          .audience(List.of("rent-cars-api"))
                          .subject(UUID.randomUUID().toString())
                          .issuedAt(now)
                          .expiresAt(now.plusSeconds(60))
                          .claim("roles", List.of("DRIVER"))
                          .build()))
              .getTokenValue();
      mvc.perform(delete(url).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
      mvc.perform(multipart("/cars").file(car()).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class Cleanup {
    @Test
    @DisplayName(
        "Given a storage failure after a partial write, when creating, then metadata rolls back and the orphan is eventually removed")
    void failedWrite() throws Exception {
      long before = jdbc.queryForObject("select count(*) from cars", Long.class);
      doAnswer(
              call -> {
                call.callRealMethod();
                throw new IllegalStateException("Storage disconnected after write");
              })
          .when(storage)
          .store(anyString(), any(byte[].class), anyString());
      mvc.perform(multipart("/cars").file(car()).file(new MockMultipartFile("images", png())))
          .andExpect(status().isInternalServerError());
      assertThat(jdbc.queryForObject("select count(*) from cars", Long.class)).isEqualTo(before);
      assertThat(jdbc.queryForObject("select count(*) from cars_images", Long.class)).isZero();
      String key = jdbc.queryForObject("select storage_key from image_cleanup_jobs", String.class);
      assertThat(Files.exists(STORAGE.resolve(key))).isTrue();
      jdbc.update("update image_cleanup_jobs set available_at=CURRENT_TIMESTAMP");
      cleanupJobs.run();
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
    }

    @Test
    @DisplayName(
        "Given files written before a failing commit, when the transaction rolls back, then durable intent remains for every orphan")
    void commitFailure() {
      assertThatThrownBy(
              () ->
                  transactions.executeWithoutResult(
                      status -> {
                        try {
                          uploaded();
                        } catch (Exception e) {
                          throw new AssertionError(e);
                        }
                        TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                              @Override
                              public void beforeCommit(boolean readOnly) {
                                throw new IllegalStateException("Commit rejected");
                              }
                            });
                      }))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Commit rejected");
      assertThat(jdbc.queryForObject("select count(*) from cars_images", Long.class)).isZero();
      String key = jdbc.queryForObject("select storage_key from image_cleanup_jobs", String.class);
      assertThat(Files.exists(STORAGE.resolve(key))).isTrue();
      jdbc.update("update image_cleanup_jobs set available_at=CURRENT_TIMESTAMP");
      cleanupJobs.run();
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
    }

    @Test
    @DisplayName(
        "Given a failed file deletion, when cleanup runs again, then the job is retained and retried")
    void retries() throws Exception {
      String url = uploaded();
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      mvc.perform(delete(url)).andExpect(status().isNoContent());
      doThrow(new IllegalStateException("Offline")).when(storage).delete(key);
      cleanupJobs.run();
      assertThat(jdbc.queryForObject("select count(*) from image_cleanup_jobs", Long.class))
          .isEqualTo(1);
      reset(storage);
      jdbc.update("update image_cleanup_jobs set available_at=CURRENT_TIMESTAMP");
      cleanupJobs.run();
      assertThat(jdbc.queryForObject("select count(*) from image_cleanup_jobs", Long.class))
          .isZero();
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
    }

    @Test
    @DisplayName(
        "Given a deleted car or category, when cascades remove its images, then cleanup is queued in the same transaction")
    void cascades() throws Exception {
      String url = uploaded();
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      mvc.perform(delete(url.substring(0, url.indexOf("/images/"))))
          .andExpect(status().isNoContent());
      cleanupJobs.run();
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
      url = uploaded();
      key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      UUID category = UUID.randomUUID();
      jdbc.update("insert into categories(id,name) values (?,?)", category, "IMAGE TEST CATEGORY");
      jdbc.update("update cars set category_id=?", category);
      jdbc.update("delete from categories where id=?", category);
      cleanupJobs.run();
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
    }

    @Test
    @DisplayName(
        "Given a locked upload intent, when another worker cleans up, then the active upload is skipped")
    void lockedUpload() throws Exception {
      String key = "cars/locked/file.png";
      cleanupJobs.prepare(key);
      storage.store(key, png(), "image/png");
      jdbc.update("update image_cleanup_jobs set available_at=CURRENT_TIMESTAMP");
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        transactions.executeWithoutResult(
            status -> {
              cleanupJobs.lock(key);
              try {
                executor.submit(() -> cleanupJobs.run()).get(10, TimeUnit.SECONDS);
              } catch (Exception e) {
                throw new AssertionError(e);
              }
              assertThat(Files.exists(STORAGE.resolve(key))).isTrue();
            });
      }
      cleanupJobs.run();
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
    }

    @Test
    @DisplayName(
        "Given an uncommitted deletion, when rolling back, then no cleanup can delete the retained image")
    void deletionRollback() throws Exception {
      String url = uploaded();
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      transactions.executeWithoutResult(
          status -> {
            jdbc.update("delete from cars_images");
            status.setRollbackOnly();
          });
      cleanupJobs.run();
      mvc.perform(get(url)).andExpect(status().isOk());
      assertThat(Files.exists(STORAGE.resolve(key))).isTrue();
    }
  }

  @Nested
  class MigrationUpgrade {
    @Test
    @DisplayName(
        "Given a V7 database containing a car, when applying V8, then the car is preserved and starts with no images")
    void preservesExistingCars() throws Exception {
      javax.sql.DataSource dataSource = jdbc.getDataSource();
      var migration =
          org.flywaydb.core.Flyway.configure()
              .dataSource(dataSource)
              .schemas("image_upgrade_test")
              .locations("classpath:db/migration")
              .target("7")
              .load();
      try {
        migration.migrate();
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into image_upgrade_test.cars(id,name,description,brand,license_plate,daily_rate_cents,fine_amount_cents,category_id)
            values (?, 'Existing car', 'Preserve me', 'Fiat', 'UPG1A23', 10000, 0, (select id from image_upgrade_test.categories limit 1))
            """,
            id);
        org.flywaydb.core.Flyway.configure()
            .dataSource(dataSource)
            .schemas("image_upgrade_test")
            .locations("classpath:db/migration")
            .load()
            .migrate();
        assertThat(
                jdbc.queryForObject(
                    "select name from image_upgrade_test.cars where id=?", String.class, id))
            .isEqualTo("Existing car");
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from image_upgrade_test.cars_images", Long.class))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from image_upgrade_test.image_cleanup_jobs", Long.class))
            .isZero();
      } finally {
        jdbc.execute("drop schema if exists image_upgrade_test cascade");
      }
    }
  }

  @Nested
  class ReviewRegressions {
    @Test
    @DisplayName(
        "Given two images and two specifications, when fetching detail, then each image is returned exactly once")
    void noDuplicateImages() throws Exception {
      var response =
          mvc.perform(
                  multipart("/cars")
                      .file(car())
                      .file(new MockMultipartFile("images", png()))
                      .file(new MockMultipartFile("images", png())))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse();
      String location = response.getHeader("Location");
      UUID carId = UUID.fromString(location.substring("/cars/".length()));
      var specs = List.of(UUID.randomUUID(), UUID.randomUUID());
      for (UUID id : specs) {
        jdbc.update(
            "insert into specifications(id,name,description) values (?,?,?)",
            id,
            id.toString(),
            "Image regression");
        jdbc.update(
            "insert into cars_specifications(car_id,specification_id) values (?,?)", carId, id);
      }
      try {
        mvc.perform(get(location))
            .andExpect(jsonPath("$.images.length()").value(2))
            .andExpect(jsonPath("$.specifications.length()").value(2));
      } finally {
        for (UUID id : specs) jdbc.update("delete from specifications where id=?", id);
      }
    }

    @Test
    @DisplayName(
        "Given a full batch of failing deletes, when cleaning again, then newer jobs still make progress")
    void noStarvation() throws Exception {
      for (int i = 0; i < 100; i++) {
        String key = "cars/failing/" + i + ".png";
        jdbc.update(
            "insert into image_cleanup_jobs(storage_key,available_at) values (?,CURRENT_TIMESTAMP - interval '1 hour')",
            key);
        doThrow(new IllegalStateException("Object retention")).when(storage).delete(key);
      }
      String key = "cars/healthy/image.png";
      storage.store(key, png(), "image/png");
      jdbc.update("insert into image_cleanup_jobs(storage_key) values (?)", key);
      cleanupJobs.run();
      cleanupJobs.run();
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
      assertThat(jdbc.queryForObject("select count(*) from image_cleanup_jobs", Long.class))
          .isEqualTo(100);
    }
  }

  @Nested
  class MultipartBoundaries {
    @Test
    @DisplayName(
        "Given too many images or missing car JSON, when creating, then the request is rejected")
    void invalidParts() throws Exception {
      var request = multipart("/cars").file(car());
      for (int i = 0; i < 7; i++) request.file(new MockMultipartFile("images", png()));
      mvc.perform(request).andExpect(status().is(422));
      mvc.perform(multipart("/cars").file(new MockMultipartFile("images", png())))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "Given multiple images, when creating, then every image is persisted and cleanup intent is cleared")
    void multipleImages() throws Exception {
      var response =
          mvc.perform(
                  multipart("/cars")
                      .file(car())
                      .file(new MockMultipartFile("images", png()))
                      .file(new MockMultipartFile("images", png())))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse();
      mvc.perform(get(response.getHeader("Location")))
          .andExpect(jsonPath("$.images.length()").value(2));
      assertThat(jdbc.queryForObject("select count(*) from image_cleanup_jobs", Long.class))
          .isZero();
    }

    @Test
    @DisplayName(
        "Given documented uploads, when reading OpenAPI, then JSON and multipart requests and public image properties are described")
    void documentation() throws Exception {
      mvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.paths['/cars'].post.requestBody.content['application/json']").exists())
          .andExpect(
              jsonPath("$.paths['/cars'].post.requestBody.content['multipart/form-data']").exists())
          .andExpect(
              jsonPath("$.paths['/cars/{carId}/images/{imageId}'].get.responses['200']").exists())
          .andExpect(
              jsonPath("$.paths['/cars/{carId}/images/{imageId}'].delete.responses['204']")
                  .exists())
          .andExpect(
              jsonPath("$.components.schemas.CarImage_Detail.properties.id.format").value("uuid"))
          .andExpect(
              jsonPath("$.components.schemas.CarImage_Detail.properties.storageKey")
                  .doesNotExist());
    }
  }
}
