package com.github.theguuholi.rentcars.cars;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Entity
@Table(name = "cars")
public class Car {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank
  @Size(max = 100)
  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @NotBlank
  @Size(max = 255)
  @Column(name = "description", nullable = false, length = 255)
  private String description;

  @NotBlank
  @Size(max = 80)
  @Column(name = "brand", nullable = false, length = 80)
  private String brand;

  @NotBlank
  @Size(max = 20)
  @Column(name = "license_plate", nullable = false, length = 20, unique = true, updatable = false)
  private String licensePlate;

  @Column(nullable = false)
  private boolean available = true;

  @NotNull
  @Column(name = "daily_rate_cents", nullable = false)
  private BrlAmount dailyRateCents;

  @NotNull
  @Column(name = "fine_amount_cents", nullable = false)
  private BrlAmount fineAmountCents;

  @NotNull
  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "cars_specifications", joinColumns = @JoinColumn(name = "car_id"))
  @Column(name = "specification_id", nullable = false)
  @Fetch(FetchMode.SUBSELECT)
  private Set<UUID> specificationIds = new HashSet<>();

  @CreationTimestamp
  @Column(name = "inserted_at", nullable = false, updatable = false)
  private Instant insertedAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Car() {}

  void apply(CarRequest fields) {
    if (fields.name() != null) name = fields.name();
    if (fields.description() != null) description = fields.description();
    if (fields.brand() != null) brand = fields.brand();
    if (fields.licensePlate() != null) registerPlate(fields.licensePlate());
    if (fields.available() != null) available = fields.available();
    if (fields.dailyRateCents() != null) dailyRateCents = new BrlAmount(fields.dailyRateCents());
    if (fields.fineAmountCents() != null) fineAmountCents = new BrlAmount(fields.fineAmountCents());
    if (fields.categoryId() != null) categoryId = fields.categoryId();
    if (fields.specificationIds() != null) {
      specificationIds.clear();
      specificationIds.addAll(fields.specificationIds());
    }
  }

  private void registerPlate(String plate) {
    String normalized = plate.strip().toUpperCase(Locale.ROOT);
    if (licensePlate != null && !licensePlate.equals(normalized)) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, "License plate cannot be changed");
    }
    licensePlate = normalized;
  }

  CarResponse toResponse() {
    return new CarResponse(
        id,
        name,
        description,
        brand,
        licensePlate,
        available,
        dailyRateCents.cents(),
        fineAmountCents.cents(),
        categoryId,
        specificationIds);
  }
}
