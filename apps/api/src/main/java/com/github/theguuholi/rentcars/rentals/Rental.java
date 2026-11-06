package com.github.theguuholi.rentcars.rentals;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.theguuholi.rentcars.cars.Car;
import com.github.theguuholi.rentcars.shared.http.JsonViews;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** An immediate rental; only finish can assign its final charge and return time. */
@Entity
@Table(name = "rentals")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonView(JsonViews.Summary.class)
@Schema(description = "An immediate vehicle rental owned by the authenticated account.")
public class Rental {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @JsonProperty
  @Schema(
      description = "Rental identifier.",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "2dfbdb0a-249e-42db-a0db-a4c066ffb067")
  private UUID id;

  @NotNull
  @Column(nullable = false, updatable = false)
  @JsonProperty
  @Schema(
      description = "Owner's account identifier from the JWT.",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "2dfbdb0a-249e-42db-a0db-a4c066ffb067")
  private UUID accountId;

  // Read the foreign key without initializing the car before acquiring its update lock.
  @Column(name = "car_id", nullable = false, insertable = false, updatable = false)
  @JsonProperty
  @Schema(
      description = "Rented vehicle identifier.",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "2dfbdb0a-249e-42db-a0db-a4c066ffb067")
  private UUID carId;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "car_id", nullable = false, updatable = false)
  @JsonProperty
  @Schema(
      description = "Rented vehicle with its current catalog prices.",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Car car;

  @NotNull
  @Column(nullable = false, updatable = false)
  @JsonProperty
  @Schema(
      description = "Server-assigned start instant in UTC.",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "2026-11-06T15:00:00Z")
  private Instant startDate;

  @NotNull
  @Column(nullable = false, updatable = false)
  @JsonProperty
  @Schema(
      description = "Expected return instant, at least 24 hours after the start.",
      example = "2026-11-08T15:00:00Z")
  private Instant expectedReturnDate;

  @Column
  @JsonProperty
  @Schema(
      description = "Actual return instant; null while open.",
      nullable = true,
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "2026-11-08T15:00:00Z")
  private Instant endDate;

  @Column
  @JsonProperty
  @Schema(
      description = "Final charge in Brazilian centavos; null while open.",
      nullable = true,
      minimum = "0",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "30000")
  private Long totalCents;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant insertedAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  protected Rental() {}

  static Rental start(UUID accountId, Car car, Instant now, Instant expectedReturnDate) {
    if (expectedReturnDate.isBefore(now.plus(Duration.ofHours(24)))) {
      throw new RentalException(
          RentalException.Reason.INVALID_PERIOD,
          "Expected return must be at least 24 hours after the start");
    }
    Rental rental = new Rental();
    rental.accountId = accountId;
    rental.car = car;
    rental.carId = car.id();
    rental.startDate = now;
    rental.expectedReturnDate = expectedReturnDate;
    return rental;
  }

  void requireOpen() {
    if (endDate != null) {
      throw new RentalException(
          RentalException.Reason.ALREADY_RETURNED, "Rental has already been returned");
    }
  }

  void finish(Instant now, long totalCents) {
    requireOpen();
    if (now.isBefore(startDate) || totalCents < 0) {
      throw new RentalException(
          RentalException.Reason.INVALID_RETURN, "Invalid return time or charge");
    }
    this.endDate = now;
    this.totalCents = totalCents;
  }

  UUID id() {
    return id;
  }

  UUID carId() {
    return carId;
  }

  Instant startDate() {
    return startDate;
  }

  Instant expectedReturnDate() {
    return expectedReturnDate;
  }
}
