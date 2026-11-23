package com.github.theguuholi.recommendationsjob;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SnapshotPublisherTest {
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
  Connection connection;

  @BeforeEach
  void database() throws Exception {
    connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    try (var sql = connection.createStatement()) {
      sql.execute("DROP TABLE IF EXISTS recommendation_snapshots");
      sql.execute(
          """
          CREATE TABLE recommendation_snapshots (
           as_of TIMESTAMPTZ PRIMARY KEY, generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
           algorithm VARCHAR(40) NOT NULL CHECK (algorithm='popular-cars-v1'),
           items JSONB NOT NULL CHECK (jsonb_typeof(items)='array' AND jsonb_array_length(items)<=1000),
           parquet_path TEXT NOT NULL)
          """);
    }
  }

  @AfterEach
  void close() throws Exception {
    connection.close();
  }

  @Nested
  class Publish {
    @Test
    void givenConcurrentBatchesForSameCutoffWhenPublishingThenExactlyOneCompleteSnapshotWins()
        throws Exception {
      var barrier = new java.util.concurrent.CyclicBarrier(2);
      var cutoff = Instant.parse("2026-11-23T00:00:00Z");
      try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
        var first =
            workers.submit(() -> {
              barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
              try (var db =
                  DriverManager.getConnection(
                      POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                new SnapshotPublisher().publish(db, cutoff, "[{\"score\":1}]", "first");
              }
              return null;
            });
        var second =
            workers.submit(() -> {
              barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
              try (var db =
                  DriverManager.getConnection(
                      POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                new SnapshotPublisher().publish(db, cutoff, "[{\"score\":2}]", "second");
              }
              return null;
            });
        first.get(15, java.util.concurrent.TimeUnit.SECONDS);
        second.get(15, java.util.concurrent.TimeUnit.SECONDS);
      }
      try (var query = connection.createStatement();
          var row =
              query.executeQuery(
                  "SELECT parquet_path, items->0->>'score' AS score FROM recommendation_snapshots")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString("parquet_path") + ":" + row.getString("score"))
            .isIn("first:1", "second:2");
        assertThat(row.next()).isFalse();
      }
    }

    @Test
    void givenRepeatedCutoffWhenPublishingThenFirstSuccessfulSnapshotRemainsImmutable()
        throws Exception {
      var publisher = new SnapshotPublisher();
      var cutoff = Instant.parse("2026-11-23T00:00:00Z");
      publisher.publish(connection, cutoff, "[]", "hdfs://first");
      publisher.publish(connection, cutoff, "[{\"score\":2}]", "hdfs://second");
      try (var query = connection.createStatement();
          var row = query.executeQuery("SELECT * FROM recommendation_snapshots")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getTimestamp("as_of").toInstant()).isEqualTo(cutoff);
        assertThat(row.getString("algorithm")).isEqualTo("popular-cars-v1");
        assertThat(row.getString("items")).isEqualTo("[]");
        assertThat(row.getString("parquet_path")).isEqualTo("hdfs://first");
        assertThat(row.getTimestamp("generated_at")).isNotNull();
        assertThat(row.next()).isFalse();
      }
    }

    @Test
    void givenExistingSnapshotWhenInsertFailsThenPreviousSnapshotSurvives() throws Exception {
      var publisher = new SnapshotPublisher();
      var first = Instant.parse("2026-11-22T00:00:00Z");
      var next = first.plusSeconds(86400);
      publisher.publish(connection, first, "[]", "old");
      assertThatThrownBy(() -> publisher.publish(connection, next, "{}", "bad"))
          .isInstanceOf(SQLException.class);
      try (var query = connection.createStatement();
          var row = query.executeQuery("SELECT as_of FROM recommendation_snapshots")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getTimestamp(1).toInstant()).isEqualTo(first);
        assertThat(row.next()).isFalse();
      }
    }

    @Test
    void givenNewerSnapshotWhenOlderBatchFinishesThenNewestCutoffRemainsLatest() throws Exception {
      var publisher = new SnapshotPublisher();
      var newer = Instant.parse("2026-11-23T00:00:00Z");
      publisher.publish(connection, newer, "[]", "new");
      publisher.publish(connection, newer.minusSeconds(1), "[]", "old");
      try (var query = connection.createStatement();
          var row =
              query.executeQuery(
                  "SELECT parquet_path FROM recommendation_snapshots ORDER BY as_of DESC LIMIT 1")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString(1)).isEqualTo("new");
      }
    }
  }
}
