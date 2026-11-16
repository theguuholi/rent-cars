package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CatalogCapacityStudyTest {
  @Autowired HikariDataSource pool;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @LocalServerPort int port;

  @Nested
  @DisplayName("GET /cars capacity study")
  class Catalog {
    @Test
    @DisplayName(
        "Given a disposable catalog, when moderate concurrent reads run, then every page is correct and pool demand is recorded")
    void recordsDemandWithoutTuningThePool() throws Exception {
      jdbc.update(
          """
          insert into cars(name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
          select 'Capacity car ' || n, 'Disposable capacity fixture', 'CapacityLab',
                 'CAPACITY' || n, c.id, 10000, 3000
          from generate_series(1,20000) n cross join categories c where c.name='SEDAN'
          """);
      jdbc.execute("ANALYZE cars");
      Path directory = Path.of("target/study-reports/catalog-capacity");
      Files.createDirectories(directory);
      Files.writeString(
          directory.resolve("environment.txt"),
          "java="
              + System.getProperty("java.version")
              + "\npostgres="
              + jdbc.queryForObject("show server_version", String.class)
              + "\nmax_connections="
              + jdbc.queryForObject("show max_connections", String.class)
              + "\nsuperuser_reserved_connections="
              + jdbc.queryForObject("show superuser_reserved_connections", String.class)
              + "\nreserved_connections="
              + jdbc.queryForObject("show reserved_connections", String.class)
              + "\nbackend_connections_before="
              + jdbc.queryForObject(
                  "select count(*) from pg_stat_activity where backend_type='client backend'",
                  Integer.class)
              + "\nmaximumPoolSize="
              + pool.getMaximumPoolSize()
              + "\nminimumIdle="
              + pool.getMinimumIdle()
              + "\nconnectionTimeoutMs="
              + pool.getConnectionTimeout()
              + "\nfixtureCars=20000\nrequestsPerStage=120\nsampleIntervalMs=10\n");
      List<String> summary = new ArrayList<>();
      summary.add(
          "concurrency,requests,p50_ms,p95_ms,max_ms,requests_per_second,peak_active,peak_waiting,peak_total");
      try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
        for (int i = 0; i < 10; i++) readPage(client);
        for (int concurrency : List.of(1, 4, 12)) {
          summary.add(measure(client, concurrency, directory));
        }
      } finally {
        jdbc.update("delete from cars where brand='CapacityLab'");
      }
      Files.write(directory.resolve("summary.csv"), summary);
      assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
      assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
    }
  }

  private double readPage(HttpClient client) throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/cars?brand=CapacityLab&size=20&page=0"))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
    long start = System.nanoTime();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    double milliseconds = (System.nanoTime() - start) / 1_000_000.0;
    assertThat(response.statusCode()).isEqualTo(200);
    var body = json.readTree(response.body());
    assertThat(body.get("content").size()).isEqualTo(20);
    assertThat(body.get("totalElements").asInt()).isEqualTo(20000);
    return milliseconds;
  }

  private String measure(HttpClient client, int concurrency, Path directory) throws Exception {
    List<int[]> samples = Collections.synchronizedList(new ArrayList<>());
    List<Double> latency = new ArrayList<>();
    long start = System.nanoTime();
    try (var sampler = Executors.newSingleThreadScheduledExecutor();
        var workers = Executors.newFixedThreadPool(concurrency)) {
      var sampling =
          sampler.scheduleAtFixedRate(
              () -> {
                var stats = pool.getHikariPoolMXBean();
                samples.add(
                    new int[] {
                      stats.getActiveConnections(),
                      stats.getThreadsAwaitingConnection(),
                      stats.getTotalConnections()
                    });
              },
              0,
              10,
              TimeUnit.MILLISECONDS);
      try {
        List<Callable<Double>> requests = new ArrayList<>();
        for (int i = 0; i < 120; i++) requests.add(() -> readPage(client));
        for (var result : workers.invokeAll(requests, 60, TimeUnit.SECONDS)) {
          latency.add(result.get());
        }
      } finally {
        sampling.cancel(false);
      }
    }
    double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
    Files.write(
        directory.resolve("latency-" + concurrency + ".csv"),
        latency.stream().map(Object::toString).toList());
    Files.write(
        directory.resolve("pool-" + concurrency + ".csv"),
        samples.stream().map(s -> s[0] + "," + s[1] + "," + s[2]).toList());
    Collections.sort(latency);
    int active = samples.stream().mapToInt(s -> s[0]).max().orElseThrow();
    int waiting = samples.stream().mapToInt(s -> s[1]).max().orElseThrow();
    int total = samples.stream().mapToInt(s -> s[2]).max().orElseThrow();
    assertThat(active).isLessThanOrEqualTo(pool.getMaximumPoolSize());
    return String.format(
        Locale.ROOT,
        "%d,120,%.2f,%.2f,%.2f,%.2f,%d,%d,%d",
        concurrency,
        latency.get(59),
        latency.get(113),
        latency.getLast(),
        120 / seconds,
        active,
        waiting,
        total);
  }
}
