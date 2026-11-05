package com.github.theguuholi.rentcars.avatar;

import static org.assertj.core.api.Assertions.*;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Real SDK requests against disposable local emulators; no cloud account or credentials required.
 */
class CloudImageStorageTest {
  static void lifecycle(ImageStorage storage) throws Exception {
    var id = UUID.randomUUID();
    var image = new byte[] {(byte) 137, 80, 78, 71};
    storage.write(id, image);
    assertThat(storage.read(id)).isEqualTo(image);
    assertThatThrownBy(() -> storage.write(id, new byte[] {9}))
        .isInstanceOf(RuntimeException.class);
    assertThat(storage.read(id)).isEqualTo(image);
    storage.delete(id);
    storage.delete(id);
    assertThatThrownBy(() -> storage.read(id)).isInstanceOf(RuntimeException.class);
  }

  @Nested
  class S3 {
    @Test
    @DisplayName(
        "Given a local S3 emulator, when uploading, reading and deleting with the real SDK, then preserve immutable bytes and allow repeated deletion")
    void contract() throws Exception {
      try (var emulator = new GenericContainer<>("adobe/s3mock:5.0.0").withExposedPorts(9090)) {

        emulator.start();
        try (var client =
            S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(
                    java.net.URI.create(
                        "http://" + emulator.getHost() + ":" + emulator.getMappedPort(9090)))
                .forcePathStyle(true)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local-user", "local-password")))
                .build()) {
          client.createBucket(CreateBucketRequest.builder().bucket("avatars").build());
          lifecycle(new S3ImageStorage(client, "avatars"));
        }
      }
    }
  }

  @Nested
  class Gcs {
    @Test
    @DisplayName(
        "Given a local GCS emulator, when uploading, reading and deleting with the real SDK, then preserve immutable bytes and allow repeated deletion")
    void contract() throws Exception {
      try (var emulator =
          new GenericContainer<>("fsouza/fake-gcs-server:1.54.0")
              .withCommand("-scheme", "http", "-port", "4443")
              .withExposedPorts(4443)) {
        emulator.start();
        try (var client =
            StorageOptions.newBuilder()
                .setProjectId("local-test")
                .setHost("http://" + emulator.getHost() + ":" + emulator.getMappedPort(4443))
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService()) {
          client.create(BucketInfo.of("avatars"));
          lifecycle(new GcsImageStorage(client, "avatars"));
        }
      }
    }
  }
}
