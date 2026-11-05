package com.github.theguuholi.rentcars.shared.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

class StorageConfigurationTest {
  StorageConfiguration configuration = new StorageConfiguration();

  StorageProperties props(String bucket, String region) {
    return new StorageProperties(
        StorageProperties.Provider.s3,
        new StorageProperties.Local(Path.of("uploads")),
        new StorageProperties.S3(bucket, region));
  }

  @Nested
  class LocalStorage {
    @Test
    @DisplayName("Given local configuration, when creating the adapter, then it uses local storage")
    void local() {
      assertThat(configuration.localStorage(props("", ""))).isInstanceOf(LocalImageStorage.class);
    }
  }

  @Nested
  class S3Storage {
    @Test
    @DisplayName("Given S3 configuration, when creating the adapter, then it uses S3 storage")
    void s3() {
      assertThat(configuration.s3Storage(mock(S3Client.class), props("bucket", "us-east-1")))
          .isInstanceOf(S3ImageStorage.class);
    }
  }

  @Nested
  class Client {
    @Test
    @DisplayName(
        "Given valid S3 settings, when creating the client, then the configured region is retained")
    void valid() {
      try (var client = configuration.s3Client(props("bucket", "sa-east-1"))) {
        assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("sa-east-1");
      }
    }

    @Test
    @DisplayName("Given missing bucket or region, when configuring S3, then startup fails clearly")
    void invalid() {
      assertThatThrownBy(() -> configuration.s3Client(props("", "us-east-1")))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> configuration.s3Client(props("bucket", "")))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
