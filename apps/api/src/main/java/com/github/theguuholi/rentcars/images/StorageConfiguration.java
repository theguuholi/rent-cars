package com.github.theguuholi.rentcars.images;

import com.google.cloud.ServiceOptions;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
class StorageConfiguration {
  @Bean
  ImageStorage imageStorage(
      @Value("${rent.storage.provider:local}") String provider,
      @Value("${rent.storage.directory:.rent-cars/uploads}") Path directory,
      @Value("${rent.storage.bucket:}") String bucket,
      ObjectProvider<S3Client> s3,
      ObjectProvider<Storage> gcs) {
    return switch (provider) {
      case "local" -> new LocalImageStorage(directory);
      case "s3" -> new S3ImageStorage(s3.getObject(), requireBucket(bucket));
      case "gcs" -> new GcsImageStorage(gcs.getObject(), requireBucket(bucket));
      default -> throw new IllegalArgumentException("Unknown rent.storage.provider: " + provider);
    };
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "rent.storage.provider", havingValue = "s3")
  static class S3Configuration {
    @Bean
    S3Client s3Client(@Value("${rent.storage.region}") String region) {
      return S3Client.builder()
          .region(Region.of(region))
          .httpClientBuilder(
              UrlConnectionHttpClient.builder()
                  .connectionTimeout(Duration.ofSeconds(5))
                  .socketTimeout(Duration.ofSeconds(20)))
          .overrideConfiguration(
              c ->
                  c.apiCallTimeout(Duration.ofSeconds(30))
                      .apiCallAttemptTimeout(Duration.ofSeconds(25)))
          .build();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "rent.storage.provider", havingValue = "gcs")
  static class GcsConfiguration {
    @Bean
    Storage gcsClient() {
      return StorageOptions.http()
          .setTransportOptions(
              HttpTransportOptions.newBuilder()
                  .setConnectTimeout(5000)
                  .setReadTimeout(20000)
                  .build())
          .setRetrySettings(
              ServiceOptions.getDefaultRetrySettings().toBuilder()
                  .setTotalTimeoutDuration(Duration.ofSeconds(30))
                  .build())
          .build()
          .getService();
    }
  }

  private static String requireBucket(String bucket) {
    if (bucket.isBlank())
      throw new IllegalArgumentException("rent.storage.bucket must not be blank");
    return bucket;
  }
}
