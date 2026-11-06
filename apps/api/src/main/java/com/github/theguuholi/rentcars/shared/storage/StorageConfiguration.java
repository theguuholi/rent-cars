package com.github.theguuholi.rentcars.shared.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfiguration {
  @Bean
  ImageStorage imageStorage(StorageProperties properties, S3Client client) {
    return new S3ImageStorage(client, properties.s3().bucket());
  }

  @Bean(destroyMethod = "close")
  DefaultCredentialsProvider s3Credentials() {
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean(destroyMethod = "close")
  S3Client s3Client(StorageProperties properties, AwsCredentialsProvider credentials) {
    if (properties.s3().bucket().isBlank() || properties.s3().region().isBlank()) {
      throw new IllegalArgumentException("S3 bucket and region must be configured");
    }
    var builder =
        S3Client.builder()
            .region(Region.of(properties.s3().region()))
            .credentialsProvider(credentials)
            .forcePathStyle(properties.s3().pathStyle())
            .overrideConfiguration(
                c ->
                    c.apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)));
    if (properties.s3().endpoint() != null) {
      builder.endpointOverride(properties.s3().endpoint());
    }
    return builder.build();
  }
}
