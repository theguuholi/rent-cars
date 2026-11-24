package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class PopularCarsClientTest {
  private HttpServer server;
  private final java.util.concurrent.ExecutorService executor =
      Executors.newVirtualThreadPerTaskExecutor();
  private final CountDownLatch release = new CountDownLatch(1);
  private volatile String body = "{\"items\":[]}";
  private volatile int status = 200;
  private volatile boolean stalled;
  private volatile String requestedPath;
  private PopularCarsClient client;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(executor);
    server.createContext(
        "/",
        exchange -> {
          requestedPath = exchange.getRequestURI().toString();
          if (stalled) {
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
              Thread.currentThread().interrupt();
            }
          }
          try (exchange) {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
          }
        });
    server.start();
    client =
        new PopularCarsClient(
            RestClient.builder(),
            "http://127.0.0.1:" + server.getAddress().getPort(),
            Duration.ofMillis(100),
            Duration.ofMillis(200));
  }

  @AfterEach
  void stopServer() {
    release.countDown();
    server.stop(0);
    executor.close();
  }

  @Nested
  class CarIds {
    @Test
    @DisplayName(
        "Given a snapshot with metadata, when requesting popularity, then ranked IDs and the bounded request are preserved")
    void ranking() {
      var first = UUID.randomUUID();
      var second = UUID.randomUUID();
      body =
          """
          {"algorithm":"popular-cars-v1","asOf":"2026-11-24T00:00:00Z","items":[
          {"carId":"%s","rank":1,"score":9},{"carId":"%s","rank":2,"score":2}]}
          """
              .formatted(first, second);
      assertThat(client.carIds()).containsExactly(first, second);
      assertThat(requestedPath).isEqualTo("/recommendations/cars?limit=100");
    }

    @ParameterizedTest(
        name =
            "Given invalid or empty body {0}, when requesting popularity, then browsing has an empty fallback")
    @ValueSource(
        strings = {
          "",
          "null",
          "{\"items\":[]}",
          "not json",
          "{}",
          "{\"items\":null}",
          "{\"items\":[null]}",
          "{\"items\":[{}]}",
          "{\"items\":[{\"carId\":\"invalid\"}]}"
        })
    void emptyOrMalformed(String response) {
      body = response;
      assertThat(client.carIds()).isEmpty();
    }

    @ParameterizedTest(
        name =
            "Given HTTP {0}, when requesting popularity, then the failure does not escape to the catalog")
    @ValueSource(ints = {400, 500, 503})
    void httpFailure(int responseStatus) {
      status = responseStatus;
      assertThat(client.carIds()).isEmpty();
    }

    @Test
    @DisplayName(
        "Given a stalled service, when the read timeout expires, then popularity returns empty without waiting for recovery")
    void timeout() {
      body = "{\"items\":[{\"carId\":\"" + UUID.randomUUID() + "\"}]}";
      stalled = true;
      assertThat(client.carIds()).isEmpty();
      assertThat(release.getCount()).isEqualTo(1);
    }
  }
}
