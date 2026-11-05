package com.github.theguuholi.rentcars.images;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Durable allocation ledger, including uploads that failed before the account was updated. */
@Schema(hidden = true)
@Entity
@Table(name = "image_objects")
public class ImageObject {
  @Id private UUID id;

  @Column(nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  protected ImageObject() {}

  public ImageObject(UUID id) {
    this.id = id;
  }

  public UUID id() {
    return id;
  }
}
