package com.github.theguuholi.recommendationsjob;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PopularityRankingTest {
  static SparkSession spark;
  static final Instant CUTOFF = Instant.parse("2026-11-23T00:00:00Z");
  @TempDir Path directory;

  @BeforeAll
  static void start() {
    spark =
        SparkSession.builder()
            .master("local[2]")
            .appName("ranking-test")
            .config("spark.ui.enabled", false)
            .config("spark.sql.shuffle.partitions", 2)
            .config("spark.sql.session.timeZone", "UTC")
            .getOrCreate();
    spark.sparkContext().setLogLevel("ERROR");
  }

  @AfterAll
  static void stop() throws Exception {
    spark.close();
  }

  static String event(
      String eventId, String rentalId, String carId, String type, String timestamp) {
    return """
    {"eventId":"%s","rentalId":"%s","schemaVersion":1,"eventType":"%s","occurredAt":"%s","payload":{"carId":"%s"}}
    """
        .formatted(eventId, rentalId, type, timestamp, carId);
  }

  static String id(int value) {
    return "00000000-0000-0000-0000-%012d".formatted(value);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"eventId", "rentalId", "payload", "eventType", "schemaVersion", "occurredAt"})
  void givenMissingRequiredFieldWhenRankingThenRejectsTheArchive(String field) throws Exception {
    String json = event(id(1), id(1), id(1), "RentalOpened", "2026-11-01T00:00:00Z");
    // Rename a required field to model an incompatible envelope without relying on Java DTOs.
    Files.writeString(
        directory.resolve("invalid.json"), json.replace("\"" + field + "\"", "\"unknown\""));
    var ranking = new PopularityRanking(spark);
    String path = directory.toString();
    assertThatThrownBy(() -> ranking.calculate(path, CUTOFF))
        .isInstanceOf(IllegalArgumentException.class);
  }

  void writeEvents(String jsonLines) throws Exception {
    int file = 0;
    for (String json : jsonLines.lines().toList()) {
      Files.writeString(directory.resolve("event-" + file++ + ".json"), json);
    }
  }

  @Nested
  class Calculate {
    @Test
    void givenPrettyPrintedArchiveWhenRankingThenReadsOneDocumentPerFile() throws Exception {
      String json =
          event(id(1), id(1), id(1), "RentalOpened", "2026-11-01T00:00:00Z").replace(",", ",\n");
      Files.writeString(directory.resolve("pretty.json"), json);
      var rows =
          new PopularityRanking(spark).calculate(directory.toString(), CUTOFF).collectAsList();
      assertThat(rows).hasSize(1);
      assertThat((Long) rows.getFirst().getAs("score")).isEqualTo(1L);
    }

    @Test
    void givenMixedCaseUuidRepresentationsWhenRankingThenDeduplicatesCanonicalIdentities()
        throws Exception {
      String identifier = "abcdefab-cdef-abcd-abcd-abcdefabcdef";
      writeEvents(
          event(identifier, identifier, identifier, "RentalOpened", "2026-11-01T00:00:00Z")
              + event(
                  identifier.toUpperCase(Locale.ROOT),
                  identifier.toUpperCase(Locale.ROOT),
                  identifier.toUpperCase(Locale.ROOT),
                  "RentalOpened",
                  "2026-11-01T00:00:00Z"));
      var rows =
          new PopularityRanking(spark).calculate(directory.toString(), CUTOFF).collectAsList();
      assertThat(rows).hasSize(1);
      assertThat(rows.getFirst().getAs("carId").toString()).isEqualTo(identifier);
      assertThat((Long) rows.getFirst().getAs("score")).isEqualTo(1L);
    }

    @Test
    void givenConflictingRentalOpeningsWhenRankingThenRejectsTheBatch() throws Exception {
      writeEvents(
          event(id(1), id(1), id(1), "RentalOpened", "2026-11-01T00:00:00Z")
              + event(id(2), id(1), id(2), "RentalOpened", "2026-11-01T00:00:00Z"));
      var ranking = new PopularityRanking(spark);
      String path = directory.toString();
      assertThatThrownBy(() -> ranking.calculate(path, CUTOFF))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Archive contains conflicting rental openings");
    }

    @Test
    void givenMoreThanOneThousandCarsWhenRankingThenBoundsSnapshotAndOrdersHighestScoreFirst()
        throws Exception {
      var json = new StringBuilder();
      for (int i = 1; i <= 1001; i++)
        json.append(event(id(i), id(i), id(i), "RentalOpened", "2026-11-01T00:00:00Z"));
      json.append(event(id(2000), id(2000), id(1001), "RentalOpened", "2026-11-02T00:00:00Z"));
      writeEvents(json.toString());
      var rows =
          new PopularityRanking(spark).calculate(directory.toString(), CUTOFF).collectAsList();
      assertThat(rows).hasSize(1000);
      assertThat(rows.getFirst().getAs("carId").toString()).isEqualTo(id(1001));
      assertThat((Long) rows.getFirst().getAs("score")).isEqualTo(2L);
      assertThat(rows.getLast().getAs("carId").toString()).isEqualTo(id(999));
      assertThat((Integer) rows.getLast().getAs("rank")).isEqualTo(1000);
    }

    @Test
    void
        givenDuplicatesReturnsAndWindowBoundariesWhenRankingThenCountsDistinctOpensAndBreaksTiesByCarId()
            throws Exception {
      String opening = event(id(1), id(1), id(1), "RentalOpened", "2026-10-24T00:00:00Z");
      writeEvents(
          opening
              + opening
              + event(id(2), id(1), id(1), "RentalReturned", "2026-11-01T00:00:00Z")
              + event(id(3), id(1), id(1), "RentalOpened", "2026-10-24T00:00:00Z")
              + event(id(4), id(2), id(2), "RentalOpened", "2026-11-22T23:59:59.999999Z")
              + event(id(5), id(3), id(3), "RentalOpened", "2026-10-23T23:59:59.999999Z")
              + event(id(6), id(4), id(4), "RentalOpened", "2026-11-23T00:00:00Z"));
      var rows =
          new PopularityRanking(spark).calculate(directory.toString(), CUTOFF).collectAsList();
      assertThat(rows).hasSize(2);
      assertThat(rows.getFirst().getAs("carId").toString()).isEqualTo(id(1));
      assertThat((Long) rows.getFirst().getAs("score")).isEqualTo(1L);
      assertThat((Integer) rows.getFirst().getAs("rank")).isEqualTo(1);
      assertThat(rows.getLast().getAs("carId").toString()).isEqualTo(id(2));
      assertThat((Integer) rows.getLast().getAs("rank")).isEqualTo(2);
    }

    @Test
    void givenEmptyDirectoryWhenRankingThenReturnsEmptyResult() {
      assertThat(new PopularityRanking(spark).calculate(directory.toString(), CUTOFF).count())
          .isZero();
    }

    @Test
    void givenInvalidEnvelopeWhenRankingThenRejectsBatch() throws Exception {
      Files.writeString(directory.resolve("invalid.json"), "{\"schemaVersion\":2}");
      var ranking = new PopularityRanking(spark);
      String path = directory.toString();
      assertThatThrownBy(() -> ranking.calculate(path, CUTOFF))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenConflictingEventIdentityWhenRankingThenRejectsBatch() throws Exception {
      writeEvents(
          event(id(1), id(1), id(1), "RentalOpened", "2026-11-01T00:00:00Z")
              + event(id(1), id(2), id(2), "RentalOpened", "2026-11-01T00:00:00Z"));
      var ranking = new PopularityRanking(spark);
      String path = directory.toString();
      assertThatThrownBy(() -> ranking.calculate(path, CUTOFF))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
