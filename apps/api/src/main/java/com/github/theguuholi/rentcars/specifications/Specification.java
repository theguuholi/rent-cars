package com.github.theguuholi.rentcars.specifications;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Schema(
    description =
        "A feature or piece of equipment that a rental vehicle can offer, such as satellite navigation.")
@Entity
@Table(name = "specifications")
public class Specification {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false)
  private String name;

  @NotBlank
  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @CreationTimestamp
  @Column(name = "inserted_at", nullable = false, updatable = false)
  private Instant insertedAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Specification() {}

  void apply(SpecificationRequest attrs) {
    if (attrs.name != null) name = attrs.name.toUpperCase(Locale.ROOT);
    if (attrs.description != null) description = attrs.description;
  }

  @Schema(
      description =
          "Server-generated identifier used to fetch, update or delete the specification.",
      example = "2dfbdb0a-249e-42db-a0db-a4c066ffb067",
      accessMode = Schema.AccessMode.READ_ONLY,
      requiredMode = Schema.RequiredMode.REQUIRED)
  public UUID getId() {
    return id;
  }

  @Schema(
      description = "Unique catalog name, returned in uppercase.",
      example = "GPS",
      maxLength = 255,
      requiredMode = Schema.RequiredMode.REQUIRED)
  public String getName() {
    return name;
  }

  @Schema(
      description = "Explanation of what the feature provides for the driver.",
      example = "Turn-by-turn satellite navigation.",
      requiredMode = Schema.RequiredMode.REQUIRED)
  public String getDescription() {
    return description;
  }
}
