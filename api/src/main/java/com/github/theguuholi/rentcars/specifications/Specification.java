package com.github.theguuholi.rentcars.specifications;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

  void apply(SpecificationRequest.Attributes attrs) {
    if (attrs.name != null) name = attrs.name.toUpperCase(Locale.ROOT);
    if (attrs.description != null) description = attrs.description;
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
}
