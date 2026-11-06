package com.github.theguuholi.rentcars.shared.storage;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Testcontainers
class S3CompatibleStorageTest {
  @Container
  static final GenericContainer<?> s3 =
      new GenericContainer<>("chrislusf/seaweedfs:4.52")
          .withCommand(
              "mini",
              "-dir=/data",
              "-ip=127.0.0.1",
              "-ip.bind=0.0.0.0",
              "-admin.ui=false",
              "-webdav=false",
              "-master.volumeSizeLimitMB=128",
              "-volume.max=4")
          .withEnv("AWS_ACCESS_KEY_ID", "rent-cars-dev")
          .withEnv("AWS_SECRET_ACCESS_KEY", "rent-cars-dev-secret")
          .withEnv("S3_BUCKET", "rent-cars-images")
          .withExposedPorts(8333, 8888)
          .waitingFor(Wait.forHttp("/buckets/rent-cars-images/").forPort(8888));

  @Nested
  class ImageLifecycle {
    @Test
    @DisplayName(
        "Given a private S3-compatible bucket, when using the configured adapter, then images round-trip without overwrites and can be removed")
    void roundTrip() {
      new ApplicationContextRunner()
          .withUserConfiguration(StorageConfiguration.class)
          .withSystemProperties(
              "aws.accessKeyId=rent-cars-dev", "aws.secretAccessKey=rent-cars-dev-secret")
          .withPropertyValues(
              "rent.storage.s3.bucket=rent-cars-images",
              "rent.storage.s3.region=us-east-1",
              "rent.storage.s3.endpoint=http://" + s3.getHost() + ":" + s3.getMappedPort(8333),
              "rent.storage.s3.path-style=true")
          .run(
              c -> {
                assertThat(c).hasNotFailed();
                var storage = c.getBean(ImageStorage.class);
                byte[] bytes = "image-content".getBytes(StandardCharsets.UTF_8);
                String key = "cars/car-id/image-id.png";
                storage.store(key, bytes, "image/png");
                assertThat(storage.load(key)).isEqualTo(bytes);
                assertThatThrownBy(() -> storage.store(key, new byte[] {1}, "image/png"))
                    .isInstanceOfSatisfying(
                        S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(412));
                assertThat(storage.load(key)).isEqualTo(bytes);
                storage.delete(key);
                assertThatThrownBy(() -> storage.load(key))
                    .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
                storage.delete(key);
              });
    }
  }
}
