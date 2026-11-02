package com.github.theguuholi.rentcars.categories;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Schema(
    description = "A vehicle body style in the rental catalog, shared by cars of the same type.")
@Entity
@Table(name = "categories")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank
  @Size(max = 50)
  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false)
  private String description;

  @CreationTimestamp
  @Column(name = "inserted_at", nullable = false, updatable = false)
  private Instant insertedAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Category() {}

  public Category(String name, String description) {
    this.name = normalizeName(name);
    this.description = description;
  }

  public void rename(String name) {
    this.name = normalizeName(name);
  }

  public void changeDescription(String description) {
    this.description = description;
  }

  @Schema(description = "Unique uppercase category name.", example = "HATCHBACK", maxLength = 50)
  public String getName() {
    return name;
  }

  @Schema(
      description = "Short explanation of the body style, suitable for display alongside the name.",
      example = "Compact car with a rear hatch.",
      maxLength = 255)
  public String getDescription() {
    return description;
  }

  /**
   * Uses a fixed locale so the same name maps to the same unique catalog key regardless of the
   * language settings of the host.
   */
  private static String normalizeName(String name) {
    return name == null ? null : name.toUpperCase(Locale.ROOT);
  }
}
