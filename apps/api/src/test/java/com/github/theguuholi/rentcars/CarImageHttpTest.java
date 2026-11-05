package com.github.theguuholi.rentcars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.theguuholi.rentcars.shared.storage.ImageStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;
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
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("rent.storage.local.directory", STORAGE::toString);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired TransactionTemplate transactions;
  @Autowired JwtEncoder encoder;
  @MockitoSpyBean ImageStorage storage;

  @AfterEach
  void cleanup() throws IOException {
    reset(storage);
    jdbc.update("delete from cars");
    try (var files = Files.walk(STORAGE)) {
      for (Path path : files.filter(Files::isRegularFile).toList()) Files.delete(path);
    }
  }

  MockMultipartFile car() {
    String category = jdbc.queryForObject("select id::text from categories limit 1", String.class);
    return carPart(
        """
        {"name":"Image car","description":"Photo example","brand":"Fiat","licensePlate":"%s",
        "dailyRateCents":10000,"fineAmountCents":0,"categoryId":"%s"}
        """
            .formatted(UUID.randomUUID().toString().substring(0, 12), category));
  }

  MockMultipartFile carPart(String value) {
    return new MockMultipartFile(
        "car", "", "application/json", value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static byte[] image(String format) throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
    return bytes.toByteArray();
  }

  String uploaded() throws Exception {
    return mvc.perform(
            multipart("/cars").file(car()).file(new MockMultipartFile("images", image("png"))))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");
  }

  String firstImage(String location) throws Exception {
    var response = mvc.perform(get(location)).andExpect(status().isOk()).andReturn().getResponse();
    return json.readTree(response.getContentAsString()).at("/images/0/id").asString();
  }

  long version(String location) throws Exception {
    return json.readTree(mvc.perform(get(location)).andReturn().getResponse().getContentAsString())
        .get("version")
        .asLong();
  }

  @Nested
  class Create {
    @Test
    @DisplayName(
        "Given PNG and JPEG files, when creating a car, then cascade persists both with server-generated keys and actual media types")
    void multipleImages() throws Exception {
      var response =
          mvc.perform(
                  multipart("/cars")
                      .file(car())
                      .file(
                          new MockMultipartFile(
                              "images", "../../bad.exe", "text/plain", image("png")))
                      .file(new MockMultipartFile("images", image("jpeg"))))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse();
      String location = response.getHeader("Location");
      var detail =
          mvc.perform(get(location))
              .andExpect(jsonPath("$.images.length()").value(2))
              .andExpect(jsonPath("$.images[0].storageKey").doesNotExist())
              .andReturn()
              .getResponse();
      for (var row : json.readTree(detail.getContentAsString()).get("images")) {
        String id = row.get("id").asString();
        String type = row.get("contentType").asString();
        mvc.perform(get(location).param("imageId", id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(type))
            .andExpect(content().bytes(image(type.equals("image/png") ? "png" : "jpeg")));
        String key =
            jdbc.queryForObject(
                "select storage_key from cars_images where id=?",
                String.class,
                UUID.fromString(id));
        assertThat(key)
            .isEqualTo(
                location.substring(1) + "/" + id + (type.equals("image/png") ? ".png" : ".jpg"));
      }
    }

    @Test
    @DisplayName("Given no image parts, when creating a multipart car, then the gallery is empty")
    void noImages() throws Exception {
      String location =
          mvc.perform(multipart("/cars").file(car()))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getHeader("Location");
      mvc.perform(get(location)).andExpect(jsonPath("$.images").isEmpty());
    }

    @Test
    @DisplayName(
        "Given invalid, empty, unsupported or truncated bytes, when uploading, then no car or image is committed")
    void invalidContent() throws Exception {
      for (byte[] bytes :
          List.of(new byte[0], new byte[] {1, 2}, image("gif"), Arrays.copyOf(image("png"), 40))) {
        mvc.perform(multipart("/cars").file(car()).file(new MockMultipartFile("images", bytes)))
            .andExpect(status().is(422));
      }
      assertThat(jdbc.queryForObject("select count(*) from cars", Long.class)).isZero();
      assertThat(jdbc.queryForObject("select count(*) from cars_images", Long.class)).isZero();
    }

    @Test
    @DisplayName(
        "Given excessive decoded dimensions or an unreadable part, when uploading, then validation rejects the image")
    void invalidDimensionsAndStream() throws Exception {
      byte[] bytes = image("png");
      ByteBuffer.wrap(bytes).putInt(16, 5000).putInt(20, 5000);
      CRC32 crc = new CRC32();
      crc.update(bytes, 12, 17);
      ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
      mvc.perform(multipart("/cars").file(car()).file(new MockMultipartFile("images", bytes)))
          .andExpect(status().is(422));
      var unreadable =
          new MockMultipartFile("images", image("png")) {
            @Override
            public byte[] getBytes() throws IOException {
              throw new IOException("Unreadable");
            }
          };
      mvc.perform(multipart("/cars").file(car()).file(unreadable)).andExpect(status().is(422));
    }

    @Test
    @DisplayName(
        "Given a partial storage write failure, when creating a car, then rollback removes the file and metadata")
    void partialWrite() throws Exception {
      doAnswer(
              call -> {
                call.callRealMethod();
                throw new IllegalStateException("Storage disconnected");
              })
          .when(storage)
          .store(anyString(), any(byte[].class), anyString());
      mvc.perform(
              multipart("/cars").file(car()).file(new MockMultipartFile("images", image("png"))))
          .andExpect(status().isInternalServerError());
      assertThat(jdbc.queryForObject("select count(*) from cars", Long.class)).isZero();
      try (var files = Files.walk(STORAGE)) {
        assertThat(files.filter(Files::isRegularFile)).isEmpty();
      }
    }

    @Test
    @DisplayName(
        "Given a failing commit after upload, when the transaction rolls back, then uploaded files are removed")
    void failedCommit() {
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
      try (var files = Files.walk(STORAGE)) {
        assertThat(files.filter(Files::isRegularFile)).isEmpty();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Nested
  class Update {
    @Test
    @DisplayName(
        "Given existing images, when appending and explicitly removing one, then retained files stay and removed files are deleted after commit")
    void appendAndRemove() throws Exception {
      String location = uploaded();
      String original = firstImage(location);
      String originalKey = jdbc.queryForObject("select storage_key from cars_images", String.class);
      mvc.perform(
              multipart(location)
                  .file(carPart("{\"expectedVersion\":" + version(location) + "}"))
                  .file(new MockMultipartFile("images", image("jpeg")))
                  .with(
                      req -> {
                        req.setMethod("PATCH");
                        return req;
                      }))
          .andExpect(status().isOk());
      mvc.perform(get(location)).andExpect(jsonPath("$.images.length()").value(2));
      assertThat(Files.exists(STORAGE.resolve(originalKey))).isTrue();
      mvc.perform(
              patch(location)
                  .contentType("application/json")
                  .content(
                      "{\"expectedVersion\":"
                          + version(location)
                          + ",\"removeImageIds\":[\""
                          + original
                          + "\"]}"))
          .andExpect(status().isOk());
      mvc.perform(get(location)).andExpect(jsonPath("$.images.length()").value(1));
      assertThat(Files.exists(STORAGE.resolve(originalKey))).isFalse();
    }

    @Test
    @DisplayName(
        "Given an omitted upload on multipart update, when changing the name, then images are retained")
    void preservesOmitted() throws Exception {
      String location = uploaded();
      mvc.perform(
              multipart(location)
                  .file(
                      carPart(
                          "{\"expectedVersion\":" + version(location) + ",\"name\":\"Updated\"}"))
                  .with(
                      req -> {
                        req.setMethod("PATCH");
                        return req;
                      }))
          .andExpect(status().isOk());
      mvc.perform(get(location))
          .andExpect(jsonPath("$.images.length()").value(1))
          .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    @DisplayName(
        "Given an unknown image or stale edit version, when updating, then existing images remain untouched")
    void rejectsInvalidSelection() throws Exception {
      String location = uploaded();
      String original = firstImage(location);
      mvc.perform(
              patch(location)
                  .contentType("application/json")
                  .content(
                      "{\"expectedVersion\":"
                          + version(location)
                          + ",\"removeImageIds\":[\""
                          + UUID.randomUUID()
                          + "\"]}"))
          .andExpect(status().is(422));
      mvc.perform(
              patch(location)
                  .contentType("application/json")
                  .content("{\"expectedVersion\":999,\"removeImageIds\":[\"" + original + "\"]}"))
          .andExpect(status().isConflict());
      mvc.perform(get(location).param("imageId", original)).andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "Given a selected removal and corrupt replacement, when updating, then rollback preserves the original file and metadata")
    void failedReplacement() throws Exception {
      String location = uploaded();
      String original = firstImage(location);
      mvc.perform(
              multipart(location)
                  .file(
                      carPart(
                          "{\"expectedVersion\":"
                              + version(location)
                              + ",\"removeImageIds\":[\""
                              + original
                              + "\"]}"))
                  .file(new MockMultipartFile("images", new byte[] {1}))
                  .with(
                      req -> {
                        req.setMethod("PATCH");
                        return req;
                      }))
          .andExpect(status().is(422));
      mvc.perform(get(location).param("imageId", original))
          .andExpect(status().isOk())
          .andExpect(content().bytes(image("png")));
    }
  }

  @Nested
  class Read {
    @Test
    @DisplayName(
        "Given an image, when reading via the car route, then it is public and cannot be addressed through another car")
    void publicOwnership() throws Exception {
      String location = uploaded();
      String id = firstImage(location);
      mvc.perform(
              get(location)
                  .param("imageId", id)
                  .with(
                      req -> {
                        req.removeHeader("Authorization");
                        return req;
                      }))
          .andExpect(status().isOk());
      mvc.perform(get("/cars/" + UUID.randomUUID()).param("imageId", id))
          .andExpect(status().isNotFound());
      mvc.perform(get(location).param("imageId", UUID.randomUUID().toString()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "Given two specifications and two images, when reading the car, then JPA returns each image once")
    void noDuplicates() throws Exception {
      String location =
          mvc.perform(
                  multipart("/cars")
                      .file(car())
                      .file(new MockMultipartFile("images", image("png")))
                      .file(new MockMultipartFile("images", image("png"))))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getHeader("Location");
      UUID carId = UUID.fromString(location.substring("/cars/".length()));
      var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
      for (UUID id : ids) {
        jdbc.update(
            "insert into specifications(id,name,description) values (?,?,?)",
            id,
            id.toString(),
            "Image test");
        jdbc.update(
            "insert into cars_specifications(car_id,specification_id) values (?,?)", carId, id);
      }
      try {
        mvc.perform(get(location))
            .andExpect(jsonPath("$.images.length()").value(2))
            .andExpect(jsonPath("$.specifications.length()").value(2));
      } finally {
        for (UUID id : ids) jdbc.update("delete from specifications where id=?", id);
      }
    }
  }

  @Nested
  class Delete {
    @Test
    @DisplayName(
        "Given a car with images, when deleting the car, then its files and image rows are removed")
    void removesFiles() throws Exception {
      String location = uploaded();
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      mvc.perform(delete(location)).andExpect(status().isNoContent());
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
      assertThat(jdbc.queryForObject("select count(*) from cars_images", Long.class)).isZero();
    }

    @Test
    @DisplayName(
        "Given storage unavailable after commit, when deleting the car, then the committed deletion is not reported as a failure")
    void cleanupFailure() throws Exception {
      String location = uploaded();
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      doThrow(new IllegalStateException("Storage offline")).when(storage).delete(key);
      mvc.perform(delete(location)).andExpect(status().isNoContent());
      mvc.perform(get(location)).andExpect(status().isNotFound());
      assertThat(Files.exists(STORAGE.resolve(key))).isTrue();
    }

    @Test
    @DisplayName(
        "Given a rolled-back deletion, when the transaction ends, then the original image remains readable")
    void rollback() throws Exception {
      String location = uploaded();
      String id = firstImage(location);
      transactions.executeWithoutResult(
          status -> {
            try {
              mvc.perform(delete(location)).andExpect(status().isNoContent());
            } catch (Exception e) {
              throw new AssertionError(e);
            }
            status.setRollbackOnly();
          });
      mvc.perform(get(location).param("imageId", id)).andExpect(status().isOk());
    }
  }

  @Nested
  class Authorization {
    @Test
    @DisplayName(
        "Given anonymous or driver credentials, when posting multipart, then existing admin permissions apply")
    void adminOnly() throws Exception {
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
      mvc.perform(multipart("/cars").file(car()).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class ContractRegressions {
    @Test
    @DisplayName(
        "Given an image-only edit, when it commits, then the car version advances and the old version cannot delete an image")
    void imageVersion() throws Exception {
      String location = uploaded();
      String original = firstImage(location);
      long before = version(location);
      mvc.perform(
              multipart(location)
                  .file(carPart("{\"expectedVersion\":" + before + "}"))
                  .file(new MockMultipartFile("images", image("png")))
                  .with(
                      req -> {
                        req.setMethod("PATCH");
                        return req;
                      }))
          .andExpect(status().isOk());
      assertThat(version(location)).isGreaterThan(before);
      mvc.perform(
              patch(location)
                  .contentType("application/json")
                  .content(
                      "{\"expectedVersion\":"
                          + before
                          + ",\"removeImageIds\":[\""
                          + original
                          + "\"]}"))
          .andExpect(status().isConflict());
    }

    @Test
    @DisplayName(
        "Given car detail and image reads on the same route, when generating OpenAPI, then imageId is optional and both response formats are documented")
    void optionalImageRead() throws Exception {
      var response =
          mvc.perform(get("/v3/api-docs"))
              .andExpect(status().isOk())
              .andExpect(
                  jsonPath(
                          "$.paths['/cars/{id}'].get.responses['200'].content['application/json'].schema['$ref']")
                      .value("#/components/schemas/Car_Detail"))
              .andExpect(
                  jsonPath(
                          "$.paths['/cars/{id}'].get.responses['200'].content['image/png'].schema.format")
                      .value("binary"))
              .andExpect(
                  jsonPath(
                          "$.paths['/cars/{id}'].get.responses['200'].content['image/jpeg'].schema.format")
                      .value("binary"))
              .andReturn()
              .getResponse();
      var params =
          json.readTree(response.getContentAsString()).at("/paths/~1cars~1{id}/get/parameters");
      boolean found = false;
      for (var parameter : params) {
        if (parameter.get("name").asString().equals("imageId")) {
          found = true;
          assertThat(parameter.path("required").asBoolean()).isFalse();
        }
      }
      assertThat(found).isTrue();
    }
  }

  @Nested
  class MigrationUpgrade {
    @Test
    @DisplayName(
        "Given a V8 database with an image, when migrating to V9, then image data remains and the queue and trigger are removed")
    void removesOnlyInfrastructure() {
      var dataSource = jdbc.getDataSource();
      try {
        org.flywaydb.core.Flyway.configure()
            .dataSource(dataSource)
            .schemas("image_upgrade_test")
            .locations("classpath:db/migration")
            .target("8")
            .load()
            .migrate();
        UUID carId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        jdbc.update(
            """
            insert into image_upgrade_test.cars(id,name,description,brand,license_plate,daily_rate_cents,fine_amount_cents,category_id)
            values (?, 'Existing', 'Keep me', 'Fiat', 'UPG1A23', 10000, 0, (select id from image_upgrade_test.categories limit 1))
            """,
            carId);
        jdbc.update(
            "insert into image_upgrade_test.cars_images(id,car_id,storage_key,content_type,size_bytes) values (?,?,?,'image/png',73)",
            imageId,
            carId,
            "cars/test.png");
        org.flywaydb.core.Flyway.configure()
            .dataSource(dataSource)
            .schemas("image_upgrade_test")
            .locations("classpath:db/migration")
            .load()
            .migrate();
        assertThat(
                jdbc.queryForObject(
                    "select storage_key from image_upgrade_test.cars_images where id=?",
                    String.class,
                    imageId))
            .isEqualTo("cars/test.png");
        assertThat(
                jdbc.queryForObject(
                    "select to_regclass('image_upgrade_test.image_cleanup_jobs')", String.class))
            .isNull();
        jdbc.update("delete from image_upgrade_test.cars where id=?", carId);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from image_upgrade_test.cars_images", Long.class))
            .isZero();
      } finally {
        jdbc.execute("drop schema if exists image_upgrade_test cascade");
      }
    }
  }
}
