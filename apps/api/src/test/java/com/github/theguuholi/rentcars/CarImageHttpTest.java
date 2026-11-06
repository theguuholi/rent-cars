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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
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

  String created() throws Exception {
    String category = jdbc.queryForObject("select id::text from categories limit 1", String.class);
    String body =
        """
        {"name":"Argo","description":"Photo example","brand":"Fiat","licensePlate":"%s",
        "dailyRateCents":10000,"fineAmountCents":0,"categoryId":"%s"}
        """
            .formatted(UUID.randomUUID().toString().substring(0, 12), category);
    return mvc.perform(post("/cars").contentType("application/json").content(body))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");
  }

  static byte[] image(String format) throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
    return bytes.toByteArray();
  }

  MockMultipartHttpServletRequestBuilder upload(String location, MockMultipartFile... files) {
    var request = multipart(location + "/images");
    for (var file : files) request.file(file);
    request.with(
        req -> {
          req.setMethod("PATCH");
          return req;
        });
    return request;
  }

  String uploaded() throws Exception {
    String location = created();
    mvc.perform(upload(location, new MockMultipartFile("images", image("png"))))
        .andExpect(status().isCreated());
    return location;
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
  class Upload {
    @Test
    @DisplayName(
        "Given an existing car, when uploading PNG and JPEG parts, then 201 includes the updated car and its Location")
    void multipleImages() throws Exception {
      String location = created();
      mvc.perform(
              upload(
                  location,
                  new MockMultipartFile("images", "../../bad.exe", "text/plain", image("png")),
                  new MockMultipartFile("images", image("jpeg"))))
          .andExpect(status().isCreated())
          .andExpect(header().string("Location", location))
          .andExpect(jsonPath("$.images.length()").value(2))
          .andExpect(jsonPath("$.category.id").exists());
      var response =
          mvc.perform(get(location))
              .andExpect(jsonPath("$.images[0].storageKey").doesNotExist())
              .andReturn()
              .getResponse();
      for (var row : json.readTree(response.getContentAsString()).get("images")) {
        String id = row.get("id").asString();
        String type = row.get("contentType").asString();
        mvc.perform(get(location).param("imageId", id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(type))
            .andExpect(content().bytes(image(type.equals("image/png") ? "png" : "jpeg")));
        assertThat(
                jdbc.queryForObject(
                    "select storage_key from cars_images where id=?",
                    String.class,
                    UUID.fromString(id)))
            .isEqualTo(
                location.substring(1) + "/" + id + (type.equals("image/png") ? ".png" : ".jpg"));
      }
    }

    @Test
    @DisplayName(
        "Given an existing gallery, when uploading more images, then old images are retained and the car version advances")
    void appends() throws Exception {
      String location = uploaded();
      String original = firstImage(location);
      long before = version(location);
      mvc.perform(upload(location, new MockMultipartFile("images", image("jpeg"))))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.images.length()").value(2));
      assertThat(version(location)).isGreaterThan(before);
      mvc.perform(get(location).param("imageId", original)).andExpect(status().isOk());
      mvc.perform(
              patch(location)
                  .contentType("application/json")
                  .content("{\"expectedVersion\":" + before + ",\"name\":\"Stale\"}"))
          .andExpect(status().isConflict());
    }

    @Test
    @DisplayName(
        "Given simultaneous uploads, when both append, then neither gallery update is lost")
    void concurrentUploads() throws Exception {
      String location = created();
      var start = new CyclicBarrier(2);
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        java.util.concurrent.Callable<Integer> task =
            () -> {
              start.await(10, TimeUnit.SECONDS);
              return mvc.perform(upload(location, new MockMultipartFile("images", image("png"))))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            };
        var first = workers.submit(task);
        var second = workers.submit(task);
        assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(201);
        assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(201);
      }
      mvc.perform(get(location)).andExpect(jsonPath("$.images.length()").value(2));
    }

    @Test
    @DisplayName(
        "Given an unknown car or missing files, when uploading, then no images are created")
    void missingInputs() throws Exception {
      mvc.perform(
              upload("/cars/" + UUID.randomUUID(), new MockMultipartFile("images", image("png"))))
          .andExpect(status().isNotFound());
      mvc.perform(upload(created())).andExpect(status().isBadRequest());
      assertThat(jdbc.queryForObject("select count(*) from cars_images", Long.class)).isZero();
    }

    @Test
    @DisplayName(
        "Given invalid, empty, unsupported or truncated bytes, when uploading, then existing car data is preserved")
    void invalidContent() throws Exception {
      String location = created();
      for (byte[] bytes :
          List.of(new byte[0], new byte[] {1, 2}, image("gif"), Arrays.copyOf(image("png"), 40))) {
        mvc.perform(upload(location, new MockMultipartFile("images", bytes)))
            .andExpect(status().is(422));
      }
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.images").isEmpty());
    }

    @Test
    @DisplayName(
        "Given excessive decoded dimensions or unreadable bytes, when uploading, then image validation rejects them")
    void invalidDimensionsAndStream() throws Exception {
      String location = created();
      byte[] bytes = image("png");
      ByteBuffer.wrap(bytes).putInt(16, 5000).putInt(20, 5000);
      CRC32 crc = new CRC32();
      crc.update(bytes, 12, 17);
      ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
      mvc.perform(upload(location, new MockMultipartFile("images", bytes)))
          .andExpect(status().is(422));
      var unreadable =
          new MockMultipartFile("images", image("png")) {
            @Override
            public byte[] getBytes() throws IOException {
              throw new IOException("Unreadable");
            }
          };
      mvc.perform(upload(location, unreadable)).andExpect(status().is(422));
    }

    @Test
    @DisplayName(
        "Given a valid image followed by a corrupt one, when uploading, then rollback removes only the new files")
    void atomicBatch() throws Exception {
      String location = uploaded();
      String original = firstImage(location);
      mvc.perform(
              upload(
                  location,
                  new MockMultipartFile("images", image("jpeg")),
                  new MockMultipartFile("images", new byte[] {1})))
          .andExpect(status().is(422));
      mvc.perform(get(location)).andExpect(jsonPath("$.images.length()").value(1));
      mvc.perform(get(location).param("imageId", original)).andExpect(status().isOk());
      try (var files = Files.walk(STORAGE)) {
        assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);
      }
    }

    @Test
    @DisplayName(
        "Given a partial write failure, when uploading, then rollback removes the partial file and preserves the car")
    void partialWrite() throws Exception {
      String location = created();
      doAnswer(
              call -> {
                call.callRealMethod();
                throw new IllegalStateException("Storage disconnected");
              })
          .when(storage)
          .store(anyString(), any(byte[].class), anyString());
      mvc.perform(upload(location, new MockMultipartFile("images", image("png"))))
          .andExpect(status().isInternalServerError());
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.images").isEmpty());
      try (var files = Files.walk(STORAGE)) {
        assertThat(files.filter(Files::isRegularFile)).isEmpty();
      }
    }

    @Test
    @DisplayName(
        "Given a commit failure after upload, when rolling back, then new image rows and files are removed")
    void failedCommit() throws Exception {
      String location = created();
      assertThatThrownBy(
              () ->
                  transactions.executeWithoutResult(
                      status -> {
                        try {
                          mvc.perform(
                                  upload(location, new MockMultipartFile("images", image("png"))))
                              .andExpect(status().isCreated());
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
      }
    }
  }

  @Nested
  class DeleteImage {
    @Test
    @DisplayName(
        "Given a car image, when removing it, then 204 deletes metadata and file and advances the car version")
    void removes() throws Exception {
      String location = uploaded();
      String id = firstImage(location);
      long before = version(location);
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      mvc.perform(delete(location + "/images/" + id))
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));
      mvc.perform(get(location)).andExpect(jsonPath("$.images").isEmpty());
      assertThat(version(location)).isGreaterThan(before);
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
      mvc.perform(delete(location + "/images/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "Given an image on another car, when removing through the wrong car, then 404 preserves its file")
    void ownership() throws Exception {
      String location = uploaded();
      String id = firstImage(location);
      String other = created();
      mvc.perform(delete(other + "/images/" + id)).andExpect(status().isNotFound());
      mvc.perform(delete("/cars/" + UUID.randomUUID() + "/images/" + id))
          .andExpect(status().isNotFound());
      mvc.perform(get(location).param("imageId", id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "Given a rolled-back image removal, when the transaction ends, then the file is retained")
    void rollback() throws Exception {
      String location = uploaded();
      String id = firstImage(location);
      transactions.executeWithoutResult(
          status -> {
            try {
              mvc.perform(delete(location + "/images/" + id)).andExpect(status().isNoContent());
            } catch (Exception e) {
              throw new AssertionError(e);
            }
            status.setRollbackOnly();
          });
      mvc.perform(get(location).param("imageId", id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "Given failed storage deletion after commit, when removing an image, then the database change succeeds and the orphan remains for manual cleanup")
    void cleanupFailure() throws Exception {
      String location = uploaded();
      String id = firstImage(location);
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      doThrow(new IllegalStateException("Storage offline")).when(storage).delete(key);
      mvc.perform(delete(location + "/images/" + id)).andExpect(status().isNoContent());
      mvc.perform(get(location)).andExpect(jsonPath("$.images").isEmpty());
      assertThat(Files.exists(STORAGE.resolve(key))).isTrue();
    }
  }

  @Nested
  class Read {
    @Test
    @DisplayName(
        "Given a stored image, when reading through the car route, then it is public and ownership is checked")
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
      mvc.perform(get(location).param("imageId", UUID.randomUUID().toString()))
          .andExpect(status().isNotFound());
      mvc.perform(get("/cars/" + UUID.randomUUID()).param("imageId", id))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "Given multiple specifications and images, when reading detail, then each image is returned exactly once")
    void noDuplicates() throws Exception {
      String location = uploaded();
      mvc.perform(upload(location, new MockMultipartFile("images", image("png"))))
          .andExpect(status().isCreated());
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
  class DeleteCar {
    @Test
    @DisplayName(
        "Given a car with images, when deleting the car, then all image rows and files are removed")
    void removesFiles() throws Exception {
      String location = uploaded();
      String key = jdbc.queryForObject("select storage_key from cars_images", String.class);
      mvc.perform(delete(location)).andExpect(status().isNoContent());
      assertThat(Files.exists(STORAGE.resolve(key))).isFalse();
      assertThat(jdbc.queryForObject("select count(*) from cars_images", Long.class)).isZero();
    }
  }

  @Nested
  class Authorization {
    @Test
    @DisplayName(
        "Given anonymous or driver credentials, when uploading or deleting images, then admin permissions are required")
    void adminOnly() throws Exception {
      String location = uploaded();
      String id = firstImage(location);
      mvc.perform(
              upload(location, new MockMultipartFile("images", image("png")))
                  .with(
                      req -> {
                        req.removeHeader("Authorization");
                        return req;
                      }))
          .andExpect(status().isUnauthorized());
      mvc.perform(
              delete(location + "/images/" + id)
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
      mvc.perform(
              upload(location, new MockMultipartFile("images", image("png")))
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
      mvc.perform(delete(location + "/images/" + id).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class ApiContract {
    @Test
    @DisplayName(
        "Given the image endpoints, when generating OpenAPI, then upload is multipart and image deletion is documented separately from car JSON")
    void documentation() throws Exception {
      mvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath(
                      "$.paths['/cars/{id}/images'].patch.requestBody.content['multipart/form-data']")
                  .exists())
          .andExpect(
              jsonPath(
                      "$.paths['/cars/{id}/images'].patch.responses['201'].content['application/json'].schema['$ref']")
                  .value("#/components/schemas/Car_Detail"))
          .andExpect(
              jsonPath("$.paths['/cars/{id}/images/{imageId}'].delete.responses['204']").exists())
          .andExpect(
              jsonPath("$.paths['/cars'].post.requestBody.content['multipart/form-data']")
                  .doesNotExist())
          .andExpect(
              jsonPath("$.paths['/cars/{id}'].patch.requestBody.content['multipart/form-data']")
                  .doesNotExist())
          .andExpect(
              jsonPath("$.components.schemas.CarRequest_Summary.properties.removeImageIds")
                  .doesNotExist())
          .andExpect(
              jsonPath(
                      "$.paths['/cars/{id}'].get.responses['200'].content['image/png'].schema.format")
                  .value("binary"));
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
