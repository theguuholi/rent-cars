package com.github.theguuholi.recommendationsjob;

import static org.apache.spark.sql.functions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

/** A deterministic thirty-day popularity baseline, not a personalized recommendation model. */
final class PopularityRanking {
  private static final String UUID_PATTERN =
      "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
  private final SparkSession spark;

  PopularityRanking(SparkSession spark) {
    this.spark = spark;
  }

  Dataset<Row> calculate(String rawPath, Instant asOf) {
    var schema =
        new StructType()
            .add("eventId", DataTypes.StringType)
            .add("eventType", DataTypes.StringType)
            .add("schemaVersion", DataTypes.IntegerType)
            .add("occurredAt", DataTypes.TimestampType)
            .add("rentalId", DataTypes.StringType)
            .add("payload", new StructType().add("carId", DataTypes.StringType));
    var events =
        spark
            .read()
            .schema(schema)
            .option("mode", "FAILFAST")
            .option("multiLine", true)
            .option("recursiveFileLookup", true)
            .option("pathGlobFilter", "*.json")
            .json(rawPath)
            .select(
                lower(col("eventId")).as("eventId"),
                col("eventType"),
                col("schemaVersion"),
                col("occurredAt"),
                lower(col("rentalId")).as("rentalId"),
                lower(col("payload.carId")).as("carId"));
    var valid =
        col("eventId")
            .rlike(UUID_PATTERN)
            .and(col("rentalId").rlike(UUID_PATTERN))
            .and(col("carId").rlike(UUID_PATTERN))
            .and(col("schemaVersion").equalTo(1))
            .and(col("eventType").isin("RentalOpened", "RentalReturned"))
            .and(col("occurredAt").isNotNull());
    if (!events.filter(not(coalesce(valid, lit(false)))).isEmpty()) {
      throw new IllegalArgumentException(
          "Archive contains an unsupported or incomplete rental event");
    }
    var uniqueEvents = events.distinct();
    if (!uniqueEvents.groupBy("eventId").count().filter(col("count").gt(1)).isEmpty()) {
      throw new IllegalArgumentException("Archive contains conflicting event identities");
    }
    var openings =
        uniqueEvents
            .filter(col("eventType").equalTo("RentalOpened"))
            .select("rentalId", "carId", "occurredAt")
            .distinct();
    if (!openings.groupBy("rentalId").count().filter(col("count").gt(1)).isEmpty()) {
      throw new IllegalArgumentException("Archive contains conflicting rental openings");
    }
    return openings
        .filter(
            col("occurredAt")
                .geq(Timestamp.from(asOf.minus(30, ChronoUnit.DAYS)))
                .and(col("occurredAt").lt(Timestamp.from(asOf))))
        .groupBy("carId")
        .agg(countDistinct("rentalId").as("score"))
        .withColumn(
            "rank", row_number().over(Window.orderBy(col("score").desc(), col("carId").asc())))
        .filter(col("rank").leq(1000))
        .orderBy("rank")
        .select("carId", "rank", "score");
  }
}
