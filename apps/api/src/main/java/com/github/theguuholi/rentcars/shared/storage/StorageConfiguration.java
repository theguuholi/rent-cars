package com.github.theguuholi.rentcars.shared.storage;

import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfiguration {
  @Bean
  ImageStorage imageStorage(StorageProperties properties, ObjectProvider<S3Client> clients) {
    return switch (properties.provider()) {
      case LOCAL -> new LocalImageStorage(properties.local().directory());
      case S3 -> new S3ImageStorage(clients.getObject(), properties.s3().bucket());
    };
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(prefix = "rent.storage", name = "provider", havingValue = "s3")
  DefaultCredentialsProvider s3Credentials() {
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(prefix = "rent.storage", name = "provider", havingValue = "s3")
  S3Client s3Client(StorageProperties properties, DefaultCredentialsProvider credentials) {
    if (properties.s3().bucket().isBlank() || properties.s3().region().isBlank()) {
      throw new IllegalArgumentException("S3 bucket and region must be configured");
    }
    return S3Client.builder()
        .region(Region.of(properties.s3().region()))
        .credentialsProvider(credentials)
        .overrideConfiguration(
            c ->
                c.apiCallTimeout(Duration.ofSeconds(30))
                    .apiCallAttemptTimeout(Duration.ofSeconds(10)))
        .build();
  }
}
