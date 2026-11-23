package com.github.theguuholi.recommendationsjob;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.Map;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RecommendationJobTest {
  @TempDir Path directory;

  @Nested
  class Main {
    @Test
    void givenProcessArgumentsWhenStartingThenUsesEnvironmentConfiguration() throws Exception {
      var args = new String[] {"raw", "curated", "2026-11-23T00:00:00Z"};
      try (var job = mockConstruction(RecommendationJob.class)) {
        RecommendationJob.main(args);
        verify(job.constructed().getFirst()).execute(args, System.getenv());
      }
    }
  }

  @Nested
  class Execute {
    @Test
    void givenMissingArgumentsWhenStartingThenRejectsBeforeConnecting() {
      var args = new String[0];
      var env = Map.<String, String>of();
      var job = new RecommendationJob();
      assertThatThrownBy(() -> job.execute(args, env))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("UTC window cutoff");
    }

    @Test
    void givenValidConfigurationWhenExecutingThenWritesParquetBeforePublishing() throws Exception {
      Path raw = Files.createDirectory(directory.resolve("raw"));
      Files.writeString(
          raw.resolve("events.json"),
          PopularityRankingTest.event(
              PopularityRankingTest.id(1),
              PopularityRankingTest.id(2),
              PopularityRankingTest.id(3),
              "RentalOpened",
              "2026-11-22T00:00:00Z"));
      var args =
          new String[] {
            raw.toString(), directory.resolve("curated").toString(), "2026-11-23T00:00:00Z"
          };
      var database = mock(Connection.class);
      var statement = mock(PreparedStatement.class);
      when(database.prepareStatement(anyString())).thenReturn(statement);
      var env =
          Map.of(
              "RECOMMENDATIONS_DATABASE_URL",
              "jdbc:test",
              "RECOMMENDATIONS_DATABASE_USER",
              "reader",
              "RECOMMENDATIONS_DATABASE_PASSWORD",
              "secret");
      System.setProperty("spark.master", "local[2]");
      System.setProperty("spark.ui.enabled", "false");
      System.setProperty("spark.sql.shuffle.partitions", "2");
      try (var driver = mockStatic(DriverManager.class)) {
        driver
            .when(() -> DriverManager.getConnection("jdbc:test", "reader", "secret"))
            .thenReturn(database);
        new RecommendationJob().execute(args, env);
      }
      verify(statement).setTimestamp(1, Timestamp.from(Instant.parse(args[2])));
      verify(statement)
          .setString(
              2, "[{\"carId\":\"00000000-0000-0000-0000-000000000003\",\"rank\":1,\"score\":1}]");
      verify(statement).executeUpdate();
      verify(database).close();
      verify(statement).close();
      try (var files = Files.walk(directory.resolve("curated"))) {
        assertThat(files.anyMatch(path -> path.getFileName().toString().endsWith(".parquet")))
            .isTrue();
      }
    }

    @Test
    void givenStorageFailureWhenRunningThenDoesNotPublishAndReleasesCachedData() throws Exception {
      Path raw = Files.createDirectory(directory.resolve("raw"));
      Path output = Files.writeString(directory.resolve("not-a-directory"), "blocked");
      var database = mock(Connection.class);
      try (var spark =
          SparkSession.builder()
              .master("local[2]")
              .appName("failure-test")
              .config("spark.ui.enabled", false)
              .getOrCreate()) {
        String input = raw.toString();
        String destination = output.toString();
        Instant cutoff = Instant.parse("2026-11-23T00:00:00Z");
        assertThatThrownBy(() -> RecommendationJob.run(spark, database, input, destination, cutoff))
            .isInstanceOf(Exception.class);
        verifyNoInteractions(database);
        assertThat(spark.sparkContext().getPersistentRDDs().size()).isZero();
      }
    }
  }
}
