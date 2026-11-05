package com.github.theguuholi.rentcars.avatar;

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
@Table(name = "avatar_objects")
class AvatarObject {
  @Id private UUID id;

  @Column(nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  protected AvatarObject() {}

  AvatarObject(UUID id) {
    this.id = id;
  }

  UUID id() {
    return id;
  }
}
