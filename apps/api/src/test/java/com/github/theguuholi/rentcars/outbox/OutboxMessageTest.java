package com.github.theguuholi.rentcars.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {
  final Instant now = Instant.parse("2026-11-16T12:00:00Z");
  final UUID eventId = UUID.randomUUID(), rentalId = UUID.randomUUID();
  final OutboxMessage message = new OutboxMessage(eventId, rentalId, "{}", now);

  @Nested
  class Lifecycle {
    @Test
    void givenNewEventWhenReadingThenPreservesIdentityPayloadAndImmediateEligibility() {
      assertThat(message.eventId()).isEqualTo(eventId);
      assertThat(message.rentalId()).isEqualTo(rentalId);
      assertThat(message.payload()).isEqualTo("{}");
      assertThat(message.ready(now)).isTrue();
      assertThat(message.ready(now.minusNanos(1))).isFalse();
      message.published(now);
      assertThat(message.publishedAt()).isEqualTo(now);
    }

    @Test
    void givenRepeatedFailureWhenRetryingThenBackoffIsBoundedAndPayloadIsStable() {
      message.failed(now);
      assertThat(message.ready(now.plusSeconds(1))).isFalse();
      assertThat(message.ready(now.plusSeconds(2))).isTrue();
      for (int i = 0; i < 40; i++) message.failed(now);
      assertThat(message.ready(now.plusSeconds(299))).isFalse();
      assertThat(message.ready(now.plusSeconds(300))).isTrue();
      assertThat(message.payload()).isEqualTo("{}");
      assertThat(message.eventId()).isEqualTo(eventId);
    }
  }
}
