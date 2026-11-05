package com.github.theguuholi.rentcars.shared.storage;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

class StorageConfigurationTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner().withUserConfiguration(StorageConfiguration.class);

  @Nested
  class ImageStorageBean {
    @Test
    @DisplayName(
        "Given the default provider, when starting Spring, then exactly one local storage is injected without AWS beans")
    void local() {
      context.run(
          c -> {
            assertThat(c)
                .hasSingleBean(ImageStorage.class)
                .doesNotHaveBean(S3Client.class)
                .doesNotHaveBean(DefaultCredentialsProvider.class);
            assertThat(c.getBean(ImageStorage.class)).isInstanceOf(LocalImageStorage.class);
          });
    }

    @Test
    @DisplayName(
        "Given an explicit local provider, when binding configuration, then the local adapter is selected")
    void explicitLocal() {
      context
          .withPropertyValues("rent.storage.provider=local")
          .run(
              c -> {
                assertThat(c).hasSingleBean(ImageStorage.class);
                assertThat(c.getBean(ImageStorage.class)).isInstanceOf(LocalImageStorage.class);
              });
    }

    @Test
    @DisplayName(
        "Given S3 settings, when starting Spring, then exactly one S3 adapter uses the configured region and credential chain")
    void s3() {
      context
          .withPropertyValues(
              "rent.storage.provider=s3",
              "rent.storage.s3.bucket=bucket",
              "rent.storage.s3.region=sa-east-1")
          .run(
              c -> {
                assertThat(c)
                    .hasSingleBean(ImageStorage.class)
                    .hasSingleBean(S3Client.class)
                    .hasSingleBean(DefaultCredentialsProvider.class);
                assertThat(c.getBean(ImageStorage.class)).isInstanceOf(S3ImageStorage.class);
                var configuration = c.getBean(S3Client.class).serviceClientConfiguration();
                assertThat(configuration.region().id()).isEqualTo("sa-east-1");
                assertThat(configuration.credentialsProvider())
                    .isSameAs(c.getBean(DefaultCredentialsProvider.class));
              });
    }
  }

  @Nested
  class S3ClientBean {
    @Test
    @DisplayName(
        "Given a missing bucket or region, when starting Spring, then configuration fails with an actionable error")
    void invalid() {
      context
          .withPropertyValues("rent.storage.provider=s3", "rent.storage.s3.region=us-east-1")
          .run(
              c ->
                  assertThat(c.getStartupFailure())
                      .hasRootCauseMessage("S3 bucket and region must be configured"));
      context
          .withPropertyValues("rent.storage.provider=s3", "rent.storage.s3.bucket=bucket")
          .run(
              c ->
                  assertThat(c.getStartupFailure())
                      .hasRootCauseMessage("S3 bucket and region must be configured"));
    }
  }
}
