package com.github.theguuholi.rentcars.shared.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

@MappedSuperclass
public abstract class VersionedEntity {
  @Version
  @Column(nullable = false)
  private long version;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(
      description = "Revision to send as expectedVersion when editing.",
      accessMode = Schema.AccessMode.READ_ONLY)
  public long getVersion() {
    return version;
  }
}
