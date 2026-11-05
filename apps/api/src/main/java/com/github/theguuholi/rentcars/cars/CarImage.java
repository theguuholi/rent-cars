package com.github.theguuholi.rentcars.cars;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A gallery entry references one immutable normalized PNG in the configured storage. */
@Entity
@Table(name = "car_images")
@Schema(description = "A car gallery image. Read its PNG at /cars/{carId}/images/{id}.")
public class CarImage {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID carId;

  @Column(nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  protected CarImage() {}

  CarImage(UUID id, UUID carId) {
    this.id = id;
    this.carId = carId;
  }

  @Schema(description = "Immutable image identifier.", accessMode = Schema.AccessMode.READ_ONLY)
  public UUID getId() {
    return id;
  }
}
