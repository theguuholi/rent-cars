package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.theguuholi.rentcars.shared.http.JsonViews;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "cars_images")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonView(JsonViews.Detail.class)
@Schema(description = "A vehicle image. Retrieve its bytes using the car and image identifiers.")
public class CarImage {
  @Id
  @JsonProperty
  @Schema(
      description = "Server-generated image identifier.",
      accessMode = Schema.AccessMode.READ_ONLY)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "car_id", nullable = false)
  private Car car;

  @NotBlank
  @Size(max = 255)
  @Column(name = "storage_key", nullable = false, unique = true)
  private String storageKey;

  @JsonProperty
  @Schema(
      description = "Validated image media type.",
      allowableValues = {"image/jpeg", "image/png"},
      example = "image/jpeg",
      accessMode = Schema.AccessMode.READ_ONLY)
  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @JsonProperty
  @Schema(
      description = "File size in bytes.",
      minimum = "1",
      maximum = "5242880",
      accessMode = Schema.AccessMode.READ_ONLY)
  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @CreationTimestamp
  @Column(name = "inserted_at", nullable = false, updatable = false)
  private Instant insertedAt;

  protected CarImage() {}

  CarImage(Car car, UUID id, String key, String contentType, long sizeBytes) {
    this.car = car;
    this.id = id;
    this.storageKey = key;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
  }

  UUID id() {
    return id;
  }

  String key() {
    return storageKey;
  }

  String contentType() {
    return contentType;
  }
}
