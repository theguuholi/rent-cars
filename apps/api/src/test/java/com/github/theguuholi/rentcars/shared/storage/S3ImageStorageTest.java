package com.github.theguuholi.rentcars.shared.storage;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

class S3ImageStorageTest {
  HttpServer server;
  S3Client client;
  S3ImageStorage storage;
  AtomicReference<String> path = new AtomicReference<>();
  AtomicReference<String> contentType = new AtomicReference<>();
  AtomicReference<String> condition = new AtomicReference<>();
  AtomicReference<byte[]> stored = new AtomicReference<>();

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          path.set(exchange.getRequestURI().getPath());
          String method = exchange.getRequestMethod();
          if (method.equals("PUT")) {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            condition.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
            stored.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(200, -1);
          } else if (method.equals("GET") && stored.get() != null) {
            byte[] bytes = stored.get();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          } else if (method.equals("DELETE")) {
            stored.set(null);
            exchange.sendResponseHeaders(204, -1);
          } else {
            byte[] error =
                "<Error><Code>NoSuchKey</Code><Message>Missing</Message></Error>".getBytes();
            exchange.sendResponseHeaders(404, error.length);
            exchange.getResponseBody().write(error);
          }
          exchange.close();
        });
    server.start();
    client =
        S3Client.builder()
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .endpointOverride(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .serviceConfiguration(c -> c.chunkedEncodingEnabled(false))
            .build();
    storage = new S3ImageStorage(client, "test-bucket");
  }

  @AfterEach
  void stop() {
    client.close();
    server.stop(0);
  }

  @Nested
  class Store {
    @Test
    @DisplayName(
        "Given image bytes, when storing through the SDK, then the bucket, key, type and non-overwrite condition reach S3")
    void stores() {
      storage.store("cars/1/2.png", new byte[] {1, 2, 3}, "image/png");
      assertThat(path.get()).isEqualTo("/test-bucket/cars/1/2.png");
      assertThat(contentType.get()).isEqualTo("image/png");
      assertThat(condition.get()).isEqualTo("*");
      assertThat(stored.get()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  class Load {
    @Test
    @DisplayName("Given an existing object, when loading, then the stored bytes are returned")
    void loads() {
      stored.set(new byte[] {1, 2});
      assertThat(storage.load("cars/a.png")).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Given a missing object, when loading, then the API receives 404")
    void missing() {
      assertThatThrownBy(() -> storage.load("missing"))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
  }

  @Nested
  class Delete {
    @Test
    @DisplayName("Given an object, when deleting twice, then removal is idempotent")
    void deletes() {
      stored.set(new byte[] {1});
      storage.delete("cars/a.png");
      storage.delete("cars/a.png");
      assertThat(stored.get()).isNull();
      assertThat(path.get()).isEqualTo("/test-bucket/cars/a.png");
    }
  }
}
