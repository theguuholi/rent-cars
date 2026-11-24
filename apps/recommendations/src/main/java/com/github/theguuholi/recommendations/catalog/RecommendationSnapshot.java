package com.github.theguuholi.recommendations.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "recommendation_snapshots")
class RecommendationSnapshot {
  protected RecommendationSnapshot() {}

  @Id private Instant asOf;
  private Instant generatedAt;
  private String algorithm;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<Item> items;

  @Schema(
      name = "RecommendedCar",
      description = "A historical popularity result; current availability is not evaluated.")
  record Item(
      @Schema(description = "Car identifier from rental events") UUID carId,
      @Schema(description = "One-based deterministic position", example = "1") int rank,
      @Schema(description = "Distinct rentals opened in the thirty-day window", example = "12")
          long score) {}

  Result result(int limit) {
    return new Result(algorithm, asOf, generatedAt, items.stream().limit(limit).toList());
  }

  @Schema(
      name = "CarRecommendations",
      description = "Latest complete snapshot; timestamps are null before the first publication.")
  record Result(
      @Schema(example = "popular-cars-v1") String algorithm,
      @Schema(description = "Exclusive UTC end of the thirty-day window") Instant asOf,
      @Schema(description = "Snapshot publication time") Instant generatedAt,
      @Schema(description = "Ranked cars, limited by the requested limit") List<Item> items) {
    static Result empty() {
      return new Result("popular-cars-v1", null, null, List.of());
    }
  }
}
