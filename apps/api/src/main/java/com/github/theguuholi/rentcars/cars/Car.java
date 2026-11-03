package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Schema(description = "A catalog vehicle with rental charges expressed in Brazilian centavos.")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@Entity
@Table(name = "cars")
public class Car {

  @JsonProperty
  @Schema(
      description = "Server-generated identifier of this rental vehicle.",
      example = "2dfbdb0a-249e-42db-a0db-a4c066ffb067",
      accessMode = Schema.AccessMode.READ_ONLY)
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @JsonProperty
  @Schema(description = "Model name.", example = "Argo", maxLength = 100)
  @NotBlank
  @Size(max = 100)
  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @JsonProperty
  @Schema(
      description = "Description of this vehicle.",
      example = "Five-door hatchback.",
      maxLength = 255)
  @NotBlank
  @Size(max = 255)
  @Column(name = "description", nullable = false, length = 255)
  private String description;

  @JsonProperty
  @Schema(description = "Vehicle manufacturer.", example = "Fiat", maxLength = 80)
  @NotBlank
  @Size(max = 80)
  @Column(name = "brand", nullable = false, length = 80)
  private String brand;

  @JsonProperty
  @Schema(
      description = "Unique, immutable uppercase license plate.",
      example = "ABC1D23",
      maxLength = 20)
  @NotBlank
  @Size(max = 20)
  @Column(name = "license_plate", nullable = false, length = 20, unique = true, updatable = false)
  private String licensePlate;

  @JsonProperty
  @Schema(description = "Whether this car can be rented.", example = "true")
  @Column(nullable = false)
  private boolean available = true;

  @JsonProperty
  @Schema(
      description = "Daily rental price in Brazilian centavos.",
      implementation = Integer.class,
      minimum = "0",
      example = "15050")
  @NotNull
  @Column(name = "daily_rate_cents", nullable = false)
  private BrlAmount dailyRateCents;

  @JsonProperty
  @Schema(
      description = "Fine in Brazilian centavos.",
      implementation = Integer.class,
      minimum = "0",
      example = "0")
  @NotNull
  @Column(name = "fine_amount_cents", nullable = false)
  private BrlAmount fineAmountCents;

  @JsonProperty
  @Schema(
      description = "Identifier of the assigned body style.",
      example = "b54350ca-00ba-4fd6-bc97-8524a4f1bfdd")
  @NotNull
  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @JsonProperty
  @Schema(description = "Identifiers of the features assigned to this vehicle.")
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
    if (fields.name != null) name = fields.name;
    if (fields.description != null) description = fields.description;
    if (fields.brand != null) brand = fields.brand;
    if (fields.licensePlate != null) registerPlate(fields.licensePlate);
    if (fields.available != null) available = fields.available;
    if (fields.dailyRateCents != null) dailyRateCents = new BrlAmount(fields.dailyRateCents);
    if (fields.fineAmountCents != null) fineAmountCents = new BrlAmount(fields.fineAmountCents);
    if (fields.categoryId != null) categoryId = fields.categoryId;
    if (fields.specificationIds != null) {
      specificationIds.clear();
      specificationIds.addAll(fields.specificationIds);
    }
  }

  /**
   * Normalizes the plate with a fixed locale so registration and later PATCH comparisons use the
   * same unique key.
   */
  private void registerPlate(String plate) {
    String normalized = plate.strip().toUpperCase(Locale.ROOT);
    if (licensePlate != null && !licensePlate.equals(normalized)) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, "License plate cannot be changed");
    }
    licensePlate = normalized;
  }

  UUID id() {
    return id;
  }
}
