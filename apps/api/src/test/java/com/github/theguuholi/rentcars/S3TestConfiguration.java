package com.github.theguuholi.rentcars;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@TestConfiguration(proxyBeanMethods = false)
class S3TestConfiguration {
  @Bean
  GenericContainer<?> s3Container() {
    return new GenericContainer<>("chrislusf/seaweedfs:4.52")
        .withCommand(
            "mini",
            "-dir=/data",
            "-ip=127.0.0.1",
            "-ip.bind=0.0.0.0",
            "-admin.ui=false",
            "-webdav=false",
            "-master.volumeSizeLimitMB=128",
            "-volume.max=4")
        .withEnv("AWS_ACCESS_KEY_ID", "test")
        .withEnv("AWS_SECRET_ACCESS_KEY", "test-secret")
        .withEnv("S3_BUCKET", "test-images")
        .withExposedPorts(8333, 8888)
        .waitingFor(Wait.forHttp("/buckets/test-images/").forPort(8888));
  }

  @Bean
  DynamicPropertyRegistrar s3Properties(GenericContainer<?> s3Container) {
    return registry -> {
      registry.add(
          "rent.storage.s3.endpoint",
          () -> "http://" + s3Container.getHost() + ":" + s3Container.getMappedPort(8333));
      registry.add("rent.storage.s3.path-style", () -> true);
    };
  }

  @Bean
  @Primary
  AwsCredentialsProvider testS3Credentials() {
    return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test-secret"));
  }
}
