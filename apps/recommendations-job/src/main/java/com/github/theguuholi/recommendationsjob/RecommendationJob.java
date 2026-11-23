package com.github.theguuholi.recommendationsjob;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.spark.sql.SparkSession;

public final class RecommendationJob {
  public static void main(String[] args) throws Exception {
    new RecommendationJob().execute(args, System.getenv());
  }

  void execute(String[] args, Map<String, String> environment) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException(
          "Expected raw HDFS path, curated HDFS path and UTC window cutoff");
    }
    Instant asOf = Instant.parse(args[2]);
    try (var connection =
            DriverManager.getConnection(
                environment.get("RECOMMENDATIONS_DATABASE_URL"),
                environment.get("RECOMMENDATIONS_DATABASE_USER"),
                environment.get("RECOMMENDATIONS_DATABASE_PASSWORD"));
        var spark =
            SparkSession.builder()
                .appName("rent-cars-popularity")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate()) {
      run(spark, connection, args[0], args[1], asOf);
    }
  }

  static void run(
      SparkSession spark, Connection database, String rawPath, String curatedRoot, Instant asOf)
      throws Exception {
    var ranking = new PopularityRanking(spark).calculate(rawPath, asOf).cache();
    try {
      String parquetPath = curatedRoot + "/" + UUID.randomUUID();
      ranking.coalesce(1).write().mode("errorifexists").parquet(parquetPath);
      String itemsJson = "[" + String.join(",", ranking.toJSON().collectAsList()) + "]";
      new SnapshotPublisher().publish(database, asOf, itemsJson, parquetPath);
    } finally {
      ranking.unpersist();
    }
  }
}
