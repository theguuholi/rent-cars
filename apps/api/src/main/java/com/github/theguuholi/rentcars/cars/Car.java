package com.github.theguuholi.rentcars.cars;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "A catalog vehicle with rental charges expressed in Brazilian centavos.")
public class Car {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank
  @Size(max = 255)
  private String name;

  @NotBlank
  @Size(max = 255)
  private String description;

  @NotBlank
  @Size(max = 255)
  private String brand;

  @NotBlank
  @Size(max = 255)
  @Column(updatable = false)
  private String licensePlate;

  private boolean available = true;
  @NotNull private BrlAmount dailyRateCents;
  @NotNull private BrlAmount fineAmountCents;
  @NotNull private UUID categoryId;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "cars_specifications", joinColumns = @JoinColumn(name = "car_id"))
  @Column(name = "specification_id")
  @Fetch(FetchMode.SUBSELECT)
  private Set<UUID> specificationIds = new HashSet<>();

  @CreationTimestamp
  @Column(updatable = false)
  private Instant insertedAt;

  @UpdateTimestamp private Instant updatedAt;

  protected Car() {}

  void apply(CarRequest fields) {
    if (fields.name != null) name = fields.name;
    if (fields.description != null) description = fields.description;
    if (fields.brand != null) brand = fields.brand;
    if (fields.licensePlate != null) {
      String normalized = fields.licensePlate.strip().toUpperCase(Locale.ROOT);
      if (licensePlate != null && !licensePlate.equals(normalized)) {
        throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_CONTENT, "License plate cannot be changed");
      }
      licensePlate = normalized;
    }
    if (fields.available != null) available = fields.available;
    if (fields.dailyRateCents != null) dailyRateCents = new BrlAmount(fields.dailyRateCents);
    if (fields.fineAmountCents != null) fineAmountCents = new BrlAmount(fields.fineAmountCents);
    if (fields.categoryId != null) categoryId = fields.categoryId;
    if (fields.specificationIds != null) {
      specificationIds.clear();
      specificationIds.addAll(fields.specificationIds);
    }
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getBrand() {
    return brand;
  }

  public String getLicensePlate() {
    return licensePlate;
  }

  public boolean isAvailable() {
    return available;
  }

  public int getDailyRateCents() {
    return dailyRateCents.cents();
  }

  public int getFineAmountCents() {
    return fineAmountCents.cents();
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public Set<UUID> getSpecificationIds() {
    return Set.copyOf(specificationIds);
  }
}
