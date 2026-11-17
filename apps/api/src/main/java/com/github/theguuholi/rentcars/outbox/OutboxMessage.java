package com.github.theguuholi.rentcars.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Durable serialized fact; retries never rebuild the payload from mutable domain state. */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long sequence;

  @Column(nullable = false, updatable = false)
  private UUID eventId;

  @Column(nullable = false, updatable = false)
  private UUID rentalId;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String payload;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private long attempts;

  @Column(nullable = false)
  private Instant nextAttemptAt;

  private Instant publishedAt;

  protected OutboxMessage() {}

  public OutboxMessage(UUID eventId, UUID rentalId, String payload, Instant occurredAt) {
    this.eventId = eventId;
    this.rentalId = rentalId;
    this.payload = payload;
    this.occurredAt = occurredAt;
    this.nextAttemptAt = occurredAt;
  }

  boolean ready(Instant now) {
    return !nextAttemptAt.isAfter(now);
  }

  Instant publishedAt() {
    return publishedAt;
  }

  void published(Instant now) {
    publishedAt = now;
  }

  void failed(Instant now) {
    attempts++;
    nextAttemptAt = now.plusSeconds(Math.min(300, 1L << Math.min(attempts, 9)));
  }

  UUID eventId() {
    return eventId;
  }

  UUID rentalId() {
    return rentalId;
  }

  String payload() {
    return payload;
  }
}
