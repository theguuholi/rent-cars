package com.github.theguuholi.rentcars.images;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class StorageConfigurationTest {
  final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(
              StorageConfiguration.class,
              org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class);

  @Nested
  class ProviderSelection {
    @Test
    @DisplayName(
        "Given no cloud settings, when starting, then only local storage is available without cloud credentials")
    void local() {
      context.run(
          c -> {
            assertThat(c)
                .hasSingleBean(ImageStorage.class)
                .doesNotHaveBean(S3Client.class)
                .doesNotHaveBean(Storage.class);
            assertThat(c.getBean(ImageStorage.class)).isInstanceOf(LocalImageStorage.class);
          });
    }

    @Test
    @DisplayName(
        "Given S3 configuration, when starting, then select S3 and do not initialize local storage or GCS")
    void s3() {
      context
          .withPropertyValues(
              "rent.storage.provider=s3",
              "rent.storage.bucket=avatars",
              "rent.storage.region=us-east-1")
          .run(
              c -> {
                assertThat(c)
                    .hasSingleBean(S3Client.class)
                    .hasSingleBean(ImageStorage.class)
                    .doesNotHaveBean(Storage.class);
                assertThat(c.getBean(ImageStorage.class)).isInstanceOf(S3ImageStorage.class);
                assertThat(c.getBean(S3Client.class).serviceClientConfiguration().region().id())
                    .isEqualTo("us-east-1");
                assertThat(c).doesNotHaveBean(LocalImageStorage.class);
              });
    }

    @Test
    @DisplayName(
        "Given GCS configuration, when starting, then initialize the default credentials client and select GCS only")
    void gcs() {
      var builder =
          StorageOptions.http()
              .setProjectId("local-test")
              .setCredentials(com.google.cloud.NoCredentials.getInstance());
      try (var defaults = mockStatic(StorageOptions.class, CALLS_REAL_METHODS)) {
        defaults.when(StorageOptions::http).thenReturn(builder);
        context
            .withPropertyValues("rent.storage.provider=gcs", "rent.storage.bucket=avatars")
            .run(
                c -> {
                  assertThat(c)
                      .hasSingleBean(Storage.class)
                      .hasSingleBean(ImageStorage.class)
                      .doesNotHaveBean(S3Client.class);
                  assertThat(c.getBean(ImageStorage.class)).isInstanceOf(GcsImageStorage.class);
                  var options = c.getBean(Storage.class).getOptions();
                  var transport =
                      (com.google.cloud.http.HttpTransportOptions) options.getTransportOptions();
                  assertThat(transport.getConnectTimeout()).isEqualTo(5000);
                  assertThat(transport.getReadTimeout()).isEqualTo(20000);
                  assertThat(options.getRetrySettings().getTotalTimeoutDuration())
                      .isEqualTo(java.time.Duration.ofSeconds(30));
                  assertThat(c).doesNotHaveBean(LocalImageStorage.class);
                });
      }
    }

    @Test
    @DisplayName(
        "Given a blank bucket, when configuring cloud storage, then fail startup with a useful configuration error")
    void blankBucket() {
      context
          .withPropertyValues(
              "rent.storage.provider=s3", "rent.storage.bucket= ", "rent.storage.region=us-east-1")
          .run(
              c ->
                  assertThat(c)
                      .hasFailed()
                      .getFailure()
                      .hasRootCauseMessage("rent.storage.bucket must not be blank"));
    }

    @Test
    @DisplayName("Given a missing cloud bucket, when configuring S3, then refuse to start")
    void missingBucket() {
      context
          .withPropertyValues("rent.storage.provider=s3", "rent.storage.region=us-east-1")
          .run(c -> assertThat(c).hasFailed());
    }
  }

  @Nested
  class ImageStorageFactory {
    @Test
    @DisplayName(
        "Given a configured bucket, when using the Spring storage factory, then forward the exact bucket to the SDK")
    void configuredBucket() throws Exception {
      var client = mock(S3Client.class);
      org.springframework.beans.factory.ObjectProvider<S3Client> s3 = mock();
      org.springframework.beans.factory.ObjectProvider<Storage> gcs = mock();
      when(s3.getObject()).thenReturn(client);
      var key = java.util.UUID.randomUUID();
      var storage =
          new StorageConfiguration()
              .imageStorage("s3", java.nio.file.Path.of("unused"), "configured-bucket", s3, gcs);
      storage.delete(key);
      verify(client)
          .deleteObject(
              software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                  .bucket("configured-bucket")
                  .key(key + ".png")
                  .build());
    }

    @Test
    @DisplayName(
        "Given an unknown provider, when Spring starts, then reject the configuration rather than using local storage")
    void unknown() {
      context
          .withPropertyValues("rent.storage.provider=unknown")
          .run(
              c ->
                  assertThat(c)
                      .hasFailed()
                      .getFailure()
                      .hasRootCauseMessage("Unknown rent.storage.provider: unknown"));
    }
  }
}
