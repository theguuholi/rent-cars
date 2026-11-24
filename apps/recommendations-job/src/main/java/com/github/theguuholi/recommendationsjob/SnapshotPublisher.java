package com.github.theguuholi.recommendationsjob;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** A single atomic insert publishes a complete snapshot. Existing cutoffs are immutable. */
final class SnapshotPublisher {
  void publish(Connection connection, Instant asOf, String itemsJson, String parquetPath)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            INSERT INTO recommendation_snapshots(as_of, algorithm, items, parquet_path)
            VALUES (?, 'popular-cars-v1', ?::jsonb, ?)
            ON CONFLICT (as_of) DO NOTHING
            """)) {
      statement.setTimestamp(1, Timestamp.from(asOf));
      statement.setString(2, itemsJson);
      statement.setString(3, parquetPath);
      statement.executeUpdate();
    }
  }
}
