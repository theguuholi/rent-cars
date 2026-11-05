package com.github.theguuholi.rentcars.shared.storage;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
@EnableScheduling
class StorageConfiguration {
  @Bean
  @ConditionalOnProperty(
      prefix = "rent.storage",
      name = "provider",
      havingValue = "local",
      matchIfMissing = true)
  ImageStorage localStorage(StorageProperties properties) {
    return new LocalImageStorage(properties.local().directory());
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(prefix = "rent.storage", name = "provider", havingValue = "s3")
  S3Client s3Client(StorageProperties properties) {
    if (properties.s3().bucket().isBlank() || properties.s3().region().isBlank()) {
      throw new IllegalArgumentException("S3 bucket and region must be configured");
    }
    return S3Client.builder()
        .region(Region.of(properties.s3().region()))
        .overrideConfiguration(
            c ->
                c.apiCallTimeout(Duration.ofSeconds(30))
                    .apiCallAttemptTimeout(Duration.ofSeconds(10)))
        .build();
  }

  @Bean
  @ConditionalOnProperty(prefix = "rent.storage", name = "provider", havingValue = "s3")
  ImageStorage s3Storage(S3Client client, StorageProperties properties) {
    return new S3ImageStorage(client, properties.s3().bucket());
  }
}
