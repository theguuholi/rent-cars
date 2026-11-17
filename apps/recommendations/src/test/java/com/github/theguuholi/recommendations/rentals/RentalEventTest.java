package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RentalEventTest {
  @Nested
  class Contract {
    @Test
    void givenVersionOneEnvelopeWhenDeserializedThenPreservesMetadataAndOpaquePayload() {
      var event =
          JsonMapper.builder().build().readValue(RentalEventsConsumerTest.JSON, RentalEvent.class);
      assertThat(event.eventId())
          .isEqualTo(UUID.fromString("b2a088d3-f66d-4f47-af43-746de9fc0375"));
      assertThat(event.eventType()).isEqualTo("RentalOpened");
      assertThat(event.schemaVersion()).isEqualTo(1);
      assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-16T15:00:00Z"));
      assertThat(event.rentalId()).isEqualTo(RentalEventsConsumerTest.RENTAL);
      assertThat(event.payload().get("accountId").asText()).isEqualTo("private-account");
    }
  }
}
