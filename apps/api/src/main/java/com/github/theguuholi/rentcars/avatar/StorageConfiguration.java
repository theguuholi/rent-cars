package com.github.theguuholi.rentcars.avatar;

import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
class StorageConfiguration {
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

    @Bean
    ImageStorage s3ImageStorage(S3Client client, @Value("${rent.storage.bucket}") String bucket) {
      return new S3ImageStorage(client, requireBucket(bucket));
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
              StorageOptions.getDefaultRetrySettings().toBuilder()
                  .setTotalTimeoutDuration(Duration.ofSeconds(30))
                  .build())
          .build()
          .getService();
    }

    @Bean
    ImageStorage gcsImageStorage(Storage client, @Value("${rent.storage.bucket}") String bucket) {
      return new GcsImageStorage(client, requireBucket(bucket));
    }
  }

  private static String requireBucket(String bucket) {
    if (bucket.isBlank())
      throw new IllegalArgumentException("rent.storage.bucket must not be blank");
    return bucket;
  }
}
